/*
 * Copyright 2013 Real Logic Ltd.
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
package uk.co.real_logic.artio.dictionary.generation;

import org.agrona.LangUtil;
import org.agrona.generation.OutputManager;
import uk.co.real_logic.artio.dictionary.CharArrayMap;
import uk.co.real_logic.artio.dictionary.ir.Dictionary;
import uk.co.real_logic.artio.dictionary.ir.Field;
import uk.co.real_logic.artio.dictionary.ir.Field.Type;
import uk.co.real_logic.artio.dictionary.ir.Field.Value;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static uk.co.real_logic.artio.dictionary.generation.GenerationUtil.*;

public final class EnumGenerator
{
    private final Dictionary dictionary;
    private final OutputManager outputManager;

    public EnumGenerator(final Dictionary dictionary, final OutputManager outputManager)
    {
        this.dictionary = dictionary;
        this.outputManager = outputManager;
    }

    public void generate()
    {
        dictionary
            .fields()
            .values()
            .stream()
            .filter(Field::isEnum)
            .filter(field -> field.type() != Type.BOOLEAN)
            .forEach(this::generateEnum);
    }

    private void generateEnum(final Field field)
    {
        final String enumName = field.name();
        final Type type = field.type();
        final List<Value> values = field.values();

        outputManager.withOutput(enumName, out ->
        {
            try
            {
                out.append(fileHeader(PARENT_PACKAGE));
                out.append(importFor(CharArrayMap.class));
                out.append(importFor(Map.class));
                out.append(importFor(HashMap.class));
                out.append(generateEnumDeclaration(enumName));

                out.append(generateEnumValues(values, type));

                out.append(generateEnumBody(enumName, type));
                out.append(generateEnumLookupMethod(enumName, values, type));
            }
            catch (final IOException e)
            {
                LangUtil.rethrowUnchecked(e);
            }
            catch (final IllegalArgumentException e)
            {
                System.err.printf("Unable to generate an enum for type: %s\n", enumName);
                System.err.println(e.getMessage());
            }
            finally
            {
                out.append("}\n");
            }
        });
    }

    private String generateEnumDeclaration(final String name)
    {
        return "public enum " + name + "\n{\n";
    }

    private String generateEnumValues(final List<Value> allValues, final Type type)
    {
        return allValues
            .stream()
            .map((value) -> format("%s%s(%s)", INDENT, value.description(), literal(value, type)))
            .collect(joining(",\n"));
    }

    private String generateEnumBody(final String name, final Type type)
    {
        final Var representation = representation(type);

        return ";\n\n" +
            representation.field() +
            constructor(name, representation) +
            representation.getter();
    }

    private String generateEnumLookupMethod(final String name, final List<Value> allValues, final Type type)
    {
        if (hasGeneratedValueOf(type))
        {
            return "";
        }

        final String optionalCharArrayDecode = optionalCharArrayDecode(name, allValues, type);

        final Var representation = representation(type);

        final String cases = allValues
            .stream()
            .map((value) -> format("        case %s: return %s;\n", literal(value, type), value.description()))
            .collect(joining());

        return format(
            "%s" +
                "    public static %s decode(%s)\n" +
                "    {\n" +
                "        switch(representation)\n" +
                "        {\n" +
                "%s" +
                "        default: throw new IllegalArgumentException(\"Unknown: \" + representation);\n" +
                "        }\n" +
                "    }\n",
            optionalCharArrayDecode,
            name,
            representation.declaration(),
            cases);
    }

    private String optionalCharArrayDecode(final String typeName, final List<Value> allValues, final Type type)
    {
        switch (type)
        {
            case STRING:
            case MULTIPLEVALUESTRING:

                final String entries = allValues
                    .stream()
                    .map((value) ->
                        format("        stringMap.put(%s, %s);\n", literal(value, type), value.description()))
                    .collect(joining());

                return format(
                    "    private static final CharArrayMap<%1$s> charMap;\n" +
                        "    static\n" +
                        "    {\n" +
                        "        final Map<String, %1$s> stringMap = new HashMap<>();\n" +
                        "%2$s" +
                        "        charMap = new CharArrayMap<>(stringMap);\n" +
                        "    }\n" +
                        "\n" +
                        "    public static %1$s decode(final char[] representation, final int length)\n" +
                        "    {\n" +
                        "        return charMap.get(representation, length);" +
                        "    }\n",
                    typeName,
                    entries);

            default:
                return "";
        }
    }

    private boolean hasGeneratedValueOf(final Type type)
    {
        switch (type)
        {
            case CURRENCY:
            case EXCHANGE:
            case COUNTRY:
            case UTCTIMEONLY:
            case UTCDATEONLY:
            case MONTHYEAR:
                return true;

            default:
                return false;
        }
    }

    private Var representation(final Type type)
    {
        final String typeValue;
        switch (type)
        {
            case STRING:
            case MULTIPLEVALUESTRING:
            case CURRENCY:
            case EXCHANGE:
            case COUNTRY:
            case UTCTIMEONLY:
            case UTCDATEONLY:
            case MONTHYEAR:
                typeValue = "String";
                break;

            default:
                typeValue = "int";
        }

        return new Var(typeValue, "representation");
    }

    private String literal(final Value value, final Type type)
    {
        final String representation = value.representation();

        switch (type)
        {
            case INT:
            case LENGTH:
            case SEQNUM:
            case NUMINGROUP:
            case DAYOFMONTH:
                // Validate that the representation genuinely is a parseable int
                Integer.parseInt(representation);
                return representation;

            case STRING:
            case MULTIPLEVALUESTRING:
            case CURRENCY:
            case EXCHANGE:
            case COUNTRY:
            case UTCTIMEONLY:
            case UTCDATEONLY:
            case MONTHYEAR:
                return '"' + representation + '"';

            case CHAR:
                if (representation.length() > 1)
                {
                    throw new IllegalArgumentException(
                        representation + " has a length of 2 and thus won't fit into a char");
                }
                return "'" + representation + "'";

            default:
                throw new IllegalArgumentException(
                    "Unknown type for creating an enum from: " + type + " for value" + value.description());
        }
    }

}
