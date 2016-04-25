/*
 * Copyright 2015-2016 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.fix_gateway.engine.logger;

import org.agrona.ErrorHandler;
import org.agrona.IoUtil;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.co.real_logic.fix_gateway.FileSystemCorruptionException;
import uk.co.real_logic.fix_gateway.engine.MappedFile;

import java.io.File;

import static org.agrona.IoUtil.deleteIfExists;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static uk.co.real_logic.fix_gateway.engine.SectorFramer.SECTOR_SIZE;
import static uk.co.real_logic.fix_gateway.engine.logger.SequenceNumberIndexDescriptor.*;

public class SequenceNumberIndexTest extends AbstractLogTest
{
    private static final int BUFFER_SIZE = 16 * 1024;

    public static final String INDEX_FILE_PATH = IoUtil.tmpDirName() + "/SequenceNumberIndex";

    private AtomicBuffer inMemoryBuffer = newBuffer();

    private ErrorHandler errorHandler = mock(ErrorHandler.class);
    private SequenceNumberIndexWriter writer;
    private SequenceNumberIndexReader reader;

    @Before
    public void setUp()
    {
        deleteIfExists(new File(INDEX_FILE_PATH));
        deleteIfExists(writablePath(INDEX_FILE_PATH));
        deleteIfExists(passingPath(INDEX_FILE_PATH));

        writer = newWriter(inMemoryBuffer);
        reader = new SequenceNumberIndexReader(inMemoryBuffer);
    }

    @Test
    public void shouldNotInitiallyKnowASequenceNumber()
    {
        assertUnknownSession();
    }

    @Test
    public void shouldStashNewSequenceNumber()
    {
        indexFixMessage();

        assertLastKnownSequenceNumberIs(SESSION_ID, SEQUENCE_NUMBER);
    }

    @Test
    public void shouldStashSequenceNumbersAgainstASessionId()
    {
        indexFixMessage();

        assertLastKnownSequenceNumberIs(SESSION_ID_2, SequenceNumberIndexReader.UNKNOWN_SESSION);
    }

    @Test
    public void shouldUpdateSequenceNumber()
    {
        final int updatedSequenceNumber = 8;

        indexFixMessage();

        bufferContainsMessage(true, SESSION_ID, updatedSequenceNumber);

        indexRecord(endPosition() + fragmentLength());

        assertLastKnownSequenceNumberIs(SESSION_ID, updatedSequenceNumber);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldValidateBufferItReadsFrom()
    {
        final AtomicBuffer tableBuffer = newBuffer();

        new SequenceNumberIndexReader(tableBuffer);
    }

    @Test
    public void shouldSaveIndexUponClose()
    {
        indexFixMessage();

        writer.close();

        final SequenceNumberIndexReader newReader = newInstanceAfterRestart();
        assertEquals(START + fragmentLength(), newReader.indexedPosition(AERON_SESSION_ID));
    }

    @Test
    public void shouldRecordIndexedPosition()
    {
        indexFixMessage();

        writer.close();

        final SequenceNumberIndexReader newReader = newInstanceAfterRestart();
        assertLastKnownSequenceNumberIs(SESSION_ID, SEQUENCE_NUMBER, newReader);
    }

    /**
     * Simulate scenario that you've crashed halfway through file flip.
     */
    @Test
    public void shouldAccountForPassingPlaceFile()
    {
        indexFixMessage();

        writer.close();

        // TODO: check that the passing place is used

        /*assertTrue("Failed to recreate crash scenario",
            new File(INDEX_FILE_PATH).renameTo(writer.passingPlace()));*/

        final SequenceNumberIndexReader newReader = newInstanceAfterRestart();
        assertLastKnownSequenceNumberIs(SESSION_ID, SEQUENCE_NUMBER, newReader);
    }

    @Test(expected = FileSystemCorruptionException.class)
    public void shouldChecksumFileToDetectCorruption()
    {
        indexFixMessage();

        writer.close();

        corruptIndexFile();

        newInstanceAfterRestart();
    }

    private void corruptIndexFile()
    {
        try (final MappedFile mappedFile = newIndexFile())
        {
            mappedFile.buffer().putBytes(0, new byte[SECTOR_SIZE / 2]);
        }
    }

    @Test
    public void shouldSaveIndexUponRotate()
    {
        final int requiredMessagesToRoll = 3;
        for (int i = 0; i <= requiredMessagesToRoll; i++)
        {
            bufferContainsMessage(true, SESSION_ID, SEQUENCE_NUMBER + i);
            indexRecord(endPosition() + (i * fragmentLength()));
        }

        try (final MappedFile mappedFile = newIndexFile())
        {
            final SequenceNumberIndexReader newReader = new SequenceNumberIndexReader(
                    mappedFile.buffer()
            );

            assertLastKnownSequenceNumberIs(SESSION_ID, SEQUENCE_NUMBER + requiredMessagesToRoll, newReader);
        }
    }

    @Test
    public void shouldAlignMessagesAndNotOverlapCheckSums()
    {
        final int initialSequenceNumber = 1;
        final int sequenceNumberDiff = 3;
        final int recordsOverlappingABlock = SECTOR_SIZE / RECORD_SIZE + 1;
        for (int i = initialSequenceNumber; i <= recordsOverlappingABlock; i++)
        {
            bufferContainsMessage(true, i, i + sequenceNumberDiff);
            indexRecord(endPosition() + (i * fragmentLength()));
        }

        writer.close();

        final SequenceNumberIndexReader newReader = newInstanceAfterRestart();
        for (int i = initialSequenceNumber; i <= recordsOverlappingABlock; i++)
        {
            assertLastKnownSequenceNumberIs(i, i + sequenceNumberDiff, newReader);
        }
    }

    @Test
    public void shouldResetSequenceNumbers()
    {
        indexFixMessage();

        writer.resetSequenceNumbers();

        assertUnknownSession();
    }

    @After
    public void verifyNoErrors()
    {
        writer.close();
        verify(errorHandler, never()).onError(any());
    }

    private SequenceNumberIndexReader newInstanceAfterRestart()
    {
        final AtomicBuffer inMemoryBuffer = newBuffer();
        newWriter(inMemoryBuffer).close();
        return new SequenceNumberIndexReader(inMemoryBuffer);
    }

    private SequenceNumberIndexWriter newWriter(final AtomicBuffer inMemoryBuffer)
    {
        final MappedFile indexFile = newIndexFile();
        return new SequenceNumberIndexWriter(inMemoryBuffer, indexFile, errorHandler);
    }

    private MappedFile newIndexFile()
    {
        return MappedFile.map(INDEX_FILE_PATH, BUFFER_SIZE);
    }

    private UnsafeBuffer newBuffer()
    {
        return new UnsafeBuffer(new byte[BUFFER_SIZE]);
    }

    private void assertUnknownSession()
    {
        assertLastKnownSequenceNumberIs(SESSION_ID, SequenceNumberIndexReader.UNKNOWN_SESSION);
    }

    private void indexFixMessage()
    {
        bufferContainsMessage(true);
        indexRecord(endPosition());
    }

    private void indexRecord(final int position)
    {
        writer.indexRecord(buffer, START, fragmentLength(), STREAM_ID, AERON_SESSION_ID, position);
    }

    private void assertLastKnownSequenceNumberIs(final long sessionId, final int expectedSequenceNumber)
    {
        assertLastKnownSequenceNumberIs(sessionId, expectedSequenceNumber, reader);
    }

    private void assertLastKnownSequenceNumberIs(
        final long sessionId,
        final long expectedSequenceNumber,
        final SequenceNumberIndexReader reader)
    {
        final int number = reader.lastKnownSequenceNumber(sessionId);
        assertEquals(expectedSequenceNumber, number);
    }

}
