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
package uk.co.real_logic.fix_gateway.dictionary.generation;

import uk.co.real_logic.agrona.generation.OutputManager;
import uk.co.real_logic.fix_gateway.dictionary.ir.DataDictionary;

import java.io.IOException;
import java.io.Writer;

import static java.lang.Character.isUpperCase;
import static java.lang.Character.toUpperCase;
import static java.util.stream.Collectors.joining;
import static uk.co.real_logic.fix_gateway.dictionary.generation.GenerationUtil.fileHeader;

public class ConstantGenerator
{
    public static final String CLASS_NAME = "Constants";

    private static final String BODY =
        "public class "+ CLASS_NAME + "\n" +
        "{\n\n";

    private final DataDictionary dictionary;
    private final String builderPackage;
    private final OutputManager outputManager;

    public ConstantGenerator(
        final DataDictionary dictionary, final String builderPackage, final OutputManager outputManager)
    {
        this.dictionary = dictionary;
        this.builderPackage = builderPackage;
        this.outputManager = outputManager;
    }

    public void generate()
    {
        try (final Writer out = outputManager.createOutput(CLASS_NAME))
        {
            out.append(fileHeader(builderPackage));
            out.append(BODY);
            out.append(generateMessageTypes());
            out.append(generateFieldTags());
            out.append("}\n");
        }
        catch (IOException e)
        {
            // TODO
            e.printStackTrace();
        }
    }

    private String generateMessageTypes()
    {
        return dictionary
            .messages()
            .stream()
            .map(message -> generateIntConstant(message.name(), message.type()))
            .collect(joining());
    }

    private String generateFieldTags()
    {
        return dictionary
            .fields()
            .values()
            .stream()
            .map(field -> generateIntConstant(field.name(), field.number()))
            .collect(joining());
    }

    private String generateIntConstant(final String name, final int number)
    {
        return String.format(
            "    public static final int %s = %d;\n\n",
            constantName(name),
            number);
    }

    private String constantName(String name)
    {
        name = name.replace("ID", "Id");
        return toUpperCase(name.charAt(0)) +
               name.substring(1)
                   .chars()
                   .mapToObj(codePoint -> (isUpperCase(codePoint) ? "_" : "") + (char) toUpperCase(codePoint))
                   .collect(joining());
    }

}