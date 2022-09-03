/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2020-2022 The JReleaser authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jreleaser.util.extensions;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jreleaser.bundle.RB;
import org.jreleaser.extensions.api.mustache.MustacheExtensionPoint;
import org.jreleaser.util.Algorithm;
import org.jreleaser.util.Constants;
import org.jreleaser.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import static java.nio.file.Files.readAllBytes;
import static org.jreleaser.util.ChecksumUtils.checksum;

/**
 * @author Andres Almiray
 * @since 1.3.0
 */
public final class DefaultMustacheExtensionPoint implements MustacheExtensionPoint {
    public void apply(Map<String, Object> props) {
        ZonedDateTime now = (ZonedDateTime) props.get(Constants.KEY_ZONED_DATE_TIME_NOW);
        if (null == now) {
            now = ZonedDateTime.now();
        }
        props.put("f_now", new TimeFormatFunction(now));

        props.put("f_trim", new TrimFunction());
        props.put("f_underscore", new UnderscoreFunction());
        props.put("f_dash", new DashFunction());
        props.put("f_slash", new SlashFunction());
        props.put("f_upper", new UpperFunction());
        props.put("f_lower", new LowerFunction());
        props.put("f_capitalize", new CapitalizeFunction());
        props.put("f_uncapitalize", new UncapitalizeFunction());
        props.put("f_md2html", new MarkdownToHtmlFunction());
        props.put("f_file_read", new FileReadFunction());
        props.put("f_file_size", new FileSizeFunction());
        EnumSet.allOf(Algorithm.class)
            .forEach(algorithm -> props.put("f_checksum_" + algorithm.formatted(), new FileChecksumFunction(algorithm)));
    }


    private static class TimeFormatFunction implements Function<String, String> {
        private final ZonedDateTime now;

        private TimeFormatFunction(ZonedDateTime now) {
            this.now = now;
        }

        @Override
        public String apply(String input) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(input);
            return now.format(formatter);
        }
    }

    private static class TrimFunction implements Function<String, String> {
        @Override
        public String apply(String input) {
            return input.trim();
        }
    }

    private static class UnderscoreFunction implements Function<String, String> {
        @Override
        public String apply(String input) {
            return input.replace(".", "_")
                .replace("-", "_")
                .replace("+", "_");
        }
    }

    private static class DashFunction implements Function<String, String> {
        @Override
        public String apply(String input) {
            return input.replace(".", "-")
                .replace("_", "-")
                .replace("+", "-");
        }
    }

    private static class SlashFunction implements Function<String, String> {
        @Override
        public String apply(String input) {
            return input.replace(".", "/")
                .replace("-", "/")
                .replace("+", "/");
        }
    }

    private static class UpperFunction implements Function<String, String> {
        @Override
        public String apply(String input) {
            return input.toUpperCase(Locale.ENGLISH);
        }
    }

    private static class LowerFunction implements Function<String, String> {
        @Override
        public String apply(String input) {
            return input.toLowerCase(Locale.ENGLISH);
        }
    }

    private static class CapitalizeFunction implements Function<String, String> {
        @Override
        public String apply(String input) {
            return StringUtils.capitalize(input);
        }
    }

    private static class UncapitalizeFunction implements Function<String, String> {
        @Override
        public String apply(String input) {
            return StringUtils.uncapitalize(input);
        }
    }

    private static class MarkdownToHtmlFunction implements Function<String, String> {
        @Override
        public String apply(String input) {
            Parser parser = Parser.builder().build();
            Node document = parser.parse(input);
            HtmlRenderer renderer = HtmlRenderer.builder().build();
            return renderer.render(document).trim();
        }
    }

    private static class FileReadFunction implements Function<Object, String> {
        @Override
        public String apply(Object input) {
            try {
                if (input instanceof Path) {
                    return new String(Files.readAllBytes((Path) input));
                } else if (input instanceof File) {
                    return new String(Files.readAllBytes(((File) input).toPath()));
                } else if (input instanceof CharSequence) {
                    return new String(Files.readAllBytes(Paths.get(String.valueOf(input).trim())));
                }
            } catch (IOException e) {
                throw new IllegalStateException(RB.$("ERROR_unexpected_file_read", input), e);
            }

            throw new IllegalStateException(RB.$("ERROR_invalid_file_input", input));
        }
    }

    private static class FileSizeFunction implements Function<Object, Long> {
        @Override
        public Long apply(Object input) {
            try {
                if (input instanceof Path) {
                    return Files.size((Path) input);
                } else if (input instanceof File) {
                    return Files.size(((File) input).toPath());
                } else if (input instanceof CharSequence) {
                    return Files.size(Paths.get(String.valueOf(input).trim()));
                }
            } catch (IOException e) {
                throw new IllegalStateException(RB.$("ERROR_unexpected_file_read", input), e);
            }

            throw new IllegalStateException(RB.$("ERROR_invalid_file_input", input));
        }
    }

    private static class FileChecksumFunction implements Function<Object, String> {
        private final Algorithm algorithm;

        public FileChecksumFunction(Algorithm algorithm) {
            this.algorithm = algorithm;
        }

        @Override
        public String apply(Object input) {
            try {
                if (input instanceof Path) {
                    return checksum(algorithm, readAllBytes((Path) input));
                } else if (input instanceof File) {
                    return checksum(algorithm, readAllBytes(((File) input).toPath()));
                } else if (input instanceof CharSequence) {
                    return checksum(algorithm, readAllBytes(Paths.get(String.valueOf(input).trim())));
                }
            } catch (IOException e) {
                throw new IllegalStateException(RB.$("ERROR_unexpected_file_read", input), e);
            }

            throw new IllegalStateException(RB.$("ERROR_invalid_file_input", input));
        }
    }
}