/*
 * Copyright 2015 Real Logic Ltd.
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
package uk.co.real_logic.fix_gateway.library.session;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static uk.co.real_logic.fix_gateway.SessionRejectReason.SENDINGTIME_ACCURACY_PROBLEM;
import static uk.co.real_logic.fix_gateway.library.session.Session.UNKNOWN;
import static uk.co.real_logic.fix_gateway.library.session.SessionState.*;

public class AcceptorSessionTest extends AbstractSessionTest
{
    private AcceptorSession session = new AcceptorSession(
        HEARTBEAT_INTERVAL, CONNECTION_ID, fakeClock, mockProxy, mockPublication, null,
        BEGIN_STRING, SENDING_TIME_WINDOW, mockReceivedMsgSeqNo,
        mockSentMsgSeqNo);

    @Test
    public void shouldInitiallyBeConnected()
    {
        assertEquals(CONNECTED, session.state());
    }

    @Test
    public void shouldBeActivatedBySuccessfulLogin()
    {
        onLogon(1);

        verifySessionSetup();
        verifyLogon();
        verifyNoFurtherMessages();
        assertState(ACTIVE);
    }

    @Test
    public void shouldRequestResendIfHighSeqNoLogon()
    {
        onLogon(3);

        verifySessionSetup();
        verifyLogon();
        verify(mockProxy).resendRequest(2, 1, 0);
        verifyNoFurtherMessages();
        assertState(AWAITING_RESEND);
    }

    @Test
    public void shouldLogoutIfFirstMessageNotALogon()
    {
        onMessage(1);

        verifyDisconnect();
        verifyNoFurtherMessages();
    }

    // See http://www.fixtradingcommunity.org/pg/discussions/topicpost/164720/fix-4x-sessionlevel-protocol-tests
    // 1d_InvalidLogonBadSendingTime.def
    @Test
    public void shouldDisconnectIfInvalidSendingTimeAtLogon()
    {
        fakeClock.advanceMilliSeconds(2 * SENDING_TIME_WINDOW);

        session().onLogon(HEARTBEAT_INTERVAL, 1, SESSION_ID, SESSION_KEY, 1, UNKNOWN, false);

        verify(mockProxy).rejectWhilstNotLoggedOn(1, SENDINGTIME_ACCURACY_PROBLEM);
    }

    protected Session session()
    {
        return session;
    }

    private void verifyLogon()
    {
        verify(mockProxy).logon(HEARTBEAT_INTERVAL, 1);
    }

    private void verifySessionSetup()
    {
        verify(mockProxy).setupSession(SESSION_ID, SESSION_KEY);
    }
}