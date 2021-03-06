/*
 * ParsedUrl.java - url parser
 * Java port of parse.py
 *
 * Copyright (C) 2016 Internet Archive
 * Copyright (C) 2016 National Library of Australia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.netpreserve.urlcanon;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.DOTALL;

public class ParsedUrl {

    private final static Pattern LEADING_JUNK_REGEX = Pattern.compile("\\A([\\x00-\\x20]*)(.*)\\Z", DOTALL);
    private final static Pattern TRAILING_JUNK_REGEX = Pattern.compile("\\A(.*?)([\\x00-\\x20]*)\\Z", DOTALL);

    private final static Pattern URL_REGEX = Pattern.compile(("\\A" +
            "(?:" +
            "   ( [a-zA-Z] [^:]* )" + // SCHEME
            "   ( : )" + // COLON_AFTER_SCHEME
            ")?" +
            "(" + // PATHISH
            "  (?: [/\\\\]* [^/\\\\?#]* )*" +
            ")" +
            "(?:" +
            "  ( [?] )" + // QUESTION_MARK
            "  ( [^#]* )" + // QUERY
            ")?" +
            "(?:" +
            "  ( [#] )" + // HASH_SIGN
            "  ( .* )" + // FRAGMENT
            ")?" +
            "\\Z").replace(" ", ""), DOTALL);

    // capturing group indices for URL_REGEX
    private final static int URL_GROUP_SCHEME = 1;
    private final static int URL_GROUP_COLON_AFTER_SCHEME = 2;
    private final static int URL_GROUP_PATHISH = 3;
    private final static int URL_GROUP_QUESTION_MARK = 4;
    private final static int URL_GROUP_QUERY = 5;
    private final static int URL_GROUP_HASH_SIGN = 6;
    private final static int URL_GROUP_FRAGMENT = 7;

    private final static Pattern SPECIAL_PATHISH_REGEX = Pattern.compile(("" +
            "( [/\\\\\\r\\n\\t]* )" + // SLASHES
            "( [^/\\\\]* )" + // AUTHORITY
            "( [/\\\\] .* )?" // PATH
    ).replace(" ", ""), DOTALL);

    private final static Pattern NONSPECIAL_PATHISH_REGEX = Pattern.compile(("" +
            "( [\\r\\n\\t]* (?:/[\\r\\n\\t]*){2} )" + // SLASHES
            "( [^/]* )" + // AUTHORITY
            "( / .* )?" // PATH
    ).replace(" ", ""), DOTALL);

    private final static Pattern FILE_PATHISH_REGEX = Pattern.compile(("" +
            "( [\\r\\n\\t]* (?:[/\\\\][\\r\\n\\t]*){2} )" + // SLASHES
            "( [^/\\\\]* )" + // HOST
            "( [/\\\\] .* )?" // PATH
    ).replace(" ", ""), DOTALL);

    // capturing group indices for {SPECIAL,NONSPECIAL,FILE}_PATHISH_REGEX
    private final static int PATHISH_GROUP_SLASHES = 1;
    private final static int PATHISH_GROUP_HOST = 2;
    private final static int PATHISH_GROUP_AUTHORITY = 2;
    private final static int PATHISH_GROUP_PATH = 3;

    private final static Pattern AUTHORITY_REGEX = Pattern.compile(("\\A" +
            "(?:" +
            "   ( [^:]* )" + // USERNAME
            "   (?:" +
            "     ( : )" + // COLON_BEFORE_PASSWORD
            "     ( .* )" + // PASSWORD
            "   )?" +
            "   ( @ )" + // AT_SIGN
            ")?" +
            "( \\[[^]]*\\] | [^:]*  )" + // HOST
            "(?:" +
            "  ( : )" + // COLON_BEFORE_PORT
            "  ( .* )" + // PORT
            ")?" +
            "\\Z").replace(" ", ""), DOTALL);

    // capturing group indices for AUTHORITY_REGEX
    private final static int AUTHORITY_GROUP_USERNAME = 1;
    private final static int AUTHORITY_GROUP_COLON_BEFORE_PASSWORD = 2;
    private final static int AUTHORITY_GROUP_PASSWORD = 3;
    private final static int AUTHORITY_GROUP_AT_SIGN = 4;
    private final static int AUTHORITY_GROUP_HOST = 5;
    private final static int AUTHORITY_GROUP_COLON_BEFORE_PORT = 6;
    private final static int AUTHORITY_GROUP_PORT = 7;

    private final static Pattern TAB_AND_NEWLINE_REGEX = Pattern.compile("[\\x09\\x0a\\x0d]");

    final static Map<String,Integer> SPECIAL_SCHEMES = initSpecialSchemes();

    private static Map<String, Integer> initSpecialSchemes() {
        Map<String,Integer> map = new HashMap<>();
        map.put("ftp", 21);
        map.put("gopher", 70);
        map.put("http", 80);
        map.put("https", 443);
        map.put("ws", 80);
        map.put("wss", 443);
        map.put("file", null);
        return map;
    }

    private ByteString leadingJunk;
    private ByteString trailingJunk;
    private ByteString scheme;
    private ByteString colonAfterScheme;
    private ByteString questionMark;
    private ByteString query;
    private ByteString hashSign;
    private ByteString fragment;
    private ByteString slashes;
    private ByteString path;
    private ByteString username;
    private ByteString colonBeforePassword;
    private ByteString password;
    private ByteString atSign;
    private ByteString host;
    private ByteString colonBeforePort;
    private ByteString port;

    //-------------------------------------------------------------------------
    //region URL Parsing
    //-------------------------------------------------------------------------

    private ParsedUrl() {
    }

    public static ParsedUrl parseUrl(String s) {
        return parseUrl(new ByteString(s));
    }

    public static ParsedUrl parseUrl(byte[] bytes) {
        return parseUrl(new ByteString(bytes));
    }

    public static ParsedUrl parseUrl(ByteString input) {
        ParsedUrl url = new ParsedUrl();

        // "leading and trailing C0 controls and space"
        Matcher m = LEADING_JUNK_REGEX.matcher(input);
        if (m.matches()) {
            url.leadingJunk = CharSequences.group(input, m, 1);
            input = CharSequences.group(input, m, 2);
        } else {
            url.leadingJunk = ByteString.EMPTY;
        }

        m = TRAILING_JUNK_REGEX.matcher(input);
        if (m.matches()) {
            url.trailingJunk = CharSequences.group(input, m, 2);
            input = CharSequences.group(input, m, 1);
        } else {
            url.trailingJunk = ByteString.EMPTY;
        }

        // parse url
        m = URL_REGEX.matcher(input);
        if (m.matches()) {
            url.scheme = CharSequences.group(input, m, URL_GROUP_SCHEME);
            url.colonAfterScheme = CharSequences.group(input, m, URL_GROUP_COLON_AFTER_SCHEME);
            url.questionMark = CharSequences.group(input, m, URL_GROUP_QUESTION_MARK);
            url.query = CharSequences.group(input, m, URL_GROUP_QUERY);
            url.hashSign = CharSequences.group(input, m, URL_GROUP_HASH_SIGN);
            url.fragment = CharSequences.group(input, m, URL_GROUP_FRAGMENT);
        } else {
            throw new AssertionError("URL_REGEX didn't match");
        }

        // we parse the authority + path into "pathish" initially so that we can
        // correctly handle file: urls
        ByteString pathish = CharSequences.group(input, m, URL_GROUP_PATHISH);

        parsePathish(url, pathish);

        return url;
    }

    /**
     * Parses "pathish", which is the section of the url after the scheme,
     * including the authority, if any, and populates fields of "url".
     */
    static void parsePathish(ParsedUrl url, ByteString pathish) {
        Matcher m;
        ByteString cleanScheme = url.scheme.replaceAll(TAB_AND_NEWLINE_REGEX, "").asciiLowerCase();
        boolean special = SPECIAL_SCHEMES.containsKey(cleanScheme.toString());

        if (cleanScheme.equalsIgnoreCase("file")) {
            m = FILE_PATHISH_REGEX.matcher(pathish);
            if (m.matches()) { // file: with host
                url.slashes = CharSequences.group(pathish, m, PATHISH_GROUP_SLASHES);
                url.host = CharSequences.group(pathish, m, PATHISH_GROUP_HOST);
                url.path = CharSequences.group(pathish, m, PATHISH_GROUP_PATH);

            } else { // file: without host
                url.slashes = ByteString.EMPTY;
                url.path = pathish;
                url.host = ByteString.EMPTY;
            }

            // these are always empty for file: URLs
            url.username = ByteString.EMPTY;
            url.colonBeforePassword = ByteString.EMPTY;
            url.password = ByteString.EMPTY;
            url.atSign = ByteString.EMPTY;
            url.colonBeforePort = ByteString.EMPTY;
            url.port = ByteString.EMPTY;
        } else {
            // not file:
            m = (special ? SPECIAL_PATHISH_REGEX : NONSPECIAL_PATHISH_REGEX).matcher(pathish);
            if (m.matches()) {
                ByteString slashes = CharSequences.group(pathish, m, PATHISH_GROUP_SLASHES);
                ByteString authority = CharSequences.group(pathish, m, PATHISH_GROUP_AUTHORITY);
                ByteString path = CharSequences.group(pathish, m, PATHISH_GROUP_PATH);

                url.slashes = slashes;
                url.path = path;

                // parse the authority
                m = AUTHORITY_REGEX.matcher(authority);
                if (m.matches()) {
                    url.username = CharSequences.group(authority, m, AUTHORITY_GROUP_USERNAME);
                    url.colonBeforePassword = CharSequences.group(authority, m, AUTHORITY_GROUP_COLON_BEFORE_PASSWORD);
                    url.password = CharSequences.group(authority, m, AUTHORITY_GROUP_PASSWORD);
                    url.atSign = CharSequences.group(authority, m, AUTHORITY_GROUP_AT_SIGN);
                    url.host = CharSequences.group(authority, m, AUTHORITY_GROUP_HOST);
                    url.colonBeforePort = CharSequences.group(authority, m, AUTHORITY_GROUP_COLON_BEFORE_PORT);
                    url.port = CharSequences.group(authority, m, AUTHORITY_GROUP_PORT);
                } else {
                    throw new AssertionError("AUTHORITY_REGEX didn't match");
                }
            } else {
                // scheme not special and pathish doesn't start with // so it's an opaque thing
                url.path = pathish;
                url.slashes = ByteString.EMPTY;
                url.username = ByteString.EMPTY;
                url.colonBeforePassword = ByteString.EMPTY;
                url.password = ByteString.EMPTY;
                url.atSign = ByteString.EMPTY;
                url.host = ByteString.EMPTY;
                url.colonBeforePort = ByteString.EMPTY;
                url.port = ByteString.EMPTY;
            }
        }
    }

    //-------------------------------------------------------------------------
    //endregion
    //-------------------------------------------------------------------------

    //-------------------------------------------------------------------------
    //region URL Formatting
    //-------------------------------------------------------------------------

    public byte[] toByteArray() {
        return buildUrl().toByteArray();
    }

    public ByteString toByteString() {
        return buildUrl().toByteString();
    }

    public String toString() {
        return buildUrl().toString();
    }

    private ByteStringBuilder buildUrl() {
        ByteStringBuilder builder = new ByteStringBuilder(leadingJunk.length() + scheme.length() + colonAfterScheme.length()
                + slashes.length() + username.length() + colonBeforePassword.length() + password.length()
                + atSign.length() + host.length() + colonBeforePort.length() + port.length() + path.length()
                + questionMark.length() + query.length() + hashSign.length()
                + fragment.length() + trailingJunk.length());
        builder.append(leadingJunk);
        builder.append(scheme);
        builder.append(colonAfterScheme);
        builder.append(slashes);
        builder.append(username);
        builder.append(colonBeforePassword);
        builder.append(password);
        builder.append(atSign);
        builder.append(host);
        builder.append(colonBeforePort);
        builder.append(port);
        builder.append(path);
        builder.append(questionMark);
        builder.append(query);
        builder.append(hashSign);
        builder.append(fragment);
        builder.append(trailingJunk);
        return builder;
    }

    //-------------------------------------------------------------------------
    //endregion
    //-------------------------------------------------------------------------

    //-------------------------------------------------------------------------
    //region SSURT Formatting
    //-------------------------------------------------------------------------

    /**
     * Format this URL with a field order suitable for sorting.
     */
    public ByteString ssurt() {
        ByteString ssurtHost = ssurtHost(host);
        ByteStringBuilder builder = new ByteStringBuilder(leadingJunk.length() + scheme.length() + colonAfterScheme.length()
                + slashes.length() + username.length() + colonBeforePassword.length() + password.length()
                + atSign.length() + ssurtHost.length() + colonBeforePort.length() + port.length() + path.length()
                + questionMark.length() + query.length() + hashSign.length()
                + fragment.length() + trailingJunk.length());
        builder.append(leadingJunk);
        builder.append(ssurtHost);
        builder.append(slashes);
        builder.append(port);
        builder.append(colonBeforePort);
        builder.append(scheme);
        builder.append(atSign);
        builder.append(username);
        builder.append(colonBeforePassword);
        builder.append(password);
        builder.append(colonAfterScheme);
        builder.append(path);
        builder.append(questionMark);
        builder.append(query);
        builder.append(hashSign);
        builder.append(fragment);
        builder.append(trailingJunk);
        return builder.toByteString();
    }

    /**
     * Reverse host unless it's an IPv4 or IPv6 address.
     */
    static ByteString ssurtHost(ByteString host) {
        if (host.isEmpty()) {
            return host;
        } else if (host.charAt(0) == '[') {
            return host;
        } else if (IpAddresses.parseIpv4(host) != -1) {
            return host;
        } else {
            return reverseHost(host);
        }
    }

    /**
     * Reverse dotted segments. Swap commas and dots. Add a trailing comma.
     *
     * "x,y.b.c" => "c,b,x.y,"
     */
    static ByteString reverseHost(ByteString host) {
        ByteStringBuilder buf = new ByteStringBuilder(host.length() + 1);
        ByteString nocommas = host.replace((byte)',', (byte)'.');
        int j = host.length();
        for (int i = host.length() - 1; i >= 0; i--) {
            if (host.charAt(i) == '.') {
                buf.append(nocommas, i + 1, j);
                buf.append(',');
                j = i;
            }
        }
        buf.append(nocommas, 0, j);
        buf.append(',');
        return buf.toByteString();
    }

    //-------------------------------------------------------------------------
    //endregion
    //-------------------------------------------------------------------------

    //-------------------------------------------------------------------------
    //region Accessors: Calculated
    //-------------------------------------------------------------------------

    ByteString hostPort() {
        ByteStringBuilder builder = new ByteStringBuilder(host.length() + colonBeforePort.length() + port.length());
        builder.append(host);
        builder.append(colonBeforePort);
        builder.append(port);
        return builder.toByteString();
    }

    //-------------------------------------------------------------------------
    //endregion
    //-------------------------------------------------------------------------

    //-------------------------------------------------------------------------
    //region Accessors: Simple
    //-------------------------------------------------------------------------

    public ByteString getLeadingJunk() {
        return leadingJunk;
    }

    public void setLeadingJunk(ByteString leadingJunk) {
        this.leadingJunk = Objects.requireNonNull(leadingJunk);
    }

    public ByteString getTrailingJunk() {
        return trailingJunk;
    }

    public void setTrailingJunk(ByteString trailingJunk) {
        this.trailingJunk = Objects.requireNonNull(trailingJunk);
    }

    public ByteString getScheme() {
        return scheme;
    }

    public void setScheme(ByteString scheme) {
        this.scheme = Objects.requireNonNull(scheme);
    }

    public ByteString getColonAfterScheme() {
        return colonAfterScheme;
    }

    public void setColonAfterScheme(ByteString colonAfterScheme) {
        this.colonAfterScheme = Objects.requireNonNull(colonAfterScheme);
    }

    public ByteString getQuestionMark() {
        return questionMark;
    }

    public void setQuestionMark(ByteString questionMark) {
        this.questionMark = Objects.requireNonNull(questionMark);
    }

    public ByteString getQuery() {
        return query;
    }

    public void setQuery(ByteString query) {
        this.query = Objects.requireNonNull(query);
    }

    public ByteString getHashSign() {
        return hashSign;
    }

    public void setHashSign(ByteString hashSign) {
        this.hashSign = Objects.requireNonNull(hashSign);
    }

    public ByteString getFragment() {
        return fragment;
    }

    public void setFragment(ByteString fragment) {
        this.fragment = Objects.requireNonNull(fragment);
    }

    public ByteString getSlashes() {
        return slashes;
    }

    public void setSlashes(ByteString slashes) {
        this.slashes = Objects.requireNonNull(slashes);
    }

    public ByteString getPath() {
        return path;
    }

    public void setPath(ByteString path) {
        this.path = Objects.requireNonNull(path);
    }

    public ByteString getUsername() {
        return username;
    }

    public void setUsername(ByteString username) {
        this.username = Objects.requireNonNull(username);
    }

    public ByteString getColonBeforePassword() {
        return colonBeforePassword;
    }

    public void setColonBeforePassword(ByteString colonBeforePassword) {
        this.colonBeforePassword = Objects.requireNonNull(colonBeforePassword);
    }

    public ByteString getPassword() {
        return password;
    }

    public void setPassword(ByteString password) {
        this.password = Objects.requireNonNull(password);
    }

    public ByteString getAtSign() {
        return atSign;
    }

    public void setAtSign(ByteString atSign) {
        this.atSign = Objects.requireNonNull(atSign);
    }

    public ByteString getHost() {
        return host;
    }

    public void setHost(ByteString host) {
        this.host = Objects.requireNonNull(host);
    }

    public ByteString getColonBeforePort() {
        return colonBeforePort;
    }

    public void setColonBeforePort(ByteString colonBeforePort) {
        this.colonBeforePort = Objects.requireNonNull(colonBeforePort);
    }

    public ByteString getPort() {
        return port;
    }

    public void setPort(ByteString port) {
        this.port = Objects.requireNonNull(port);
    }

    //-------------------------------------------------------------------------
    //endregion
    //-------------------------------------------------------------------------
}
