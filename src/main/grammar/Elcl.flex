package dev.erbsland.elcl.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import dev.erbsland.elcl.psi.ElclTypes;

%%

%class _ElclLexer
%implements FlexLexer
%unicode
%public
%function advance
%type IElementType
%eof{ return;
%eof}

%state SECTION
%state VALUE
%state MULTI_TEXT
%state MULTI_CODE
%state MULTI_REGEX
%state MULTI_BYTES

CRLF=\r\n|\n|\r
SPACE=[ \t\f]+
COMMENT=#[^\r\n]*
NAME=\p{L}[\p{L}\p{N}]*([ _][\p{L}\p{N}]+)*
META_NAME=@\p{L}[\p{L}\p{N}_]*
TEXT="\""([^\"\\\r\n]|\\.)*"\""
CODE=`[^`\r\n]*`
REGEX="/"([^/\\\r\n]|\\.)*"/"
BYTES=<([0-9a-fA-F]{2}|[ \t\r\n])+>
SIGN=[+-]?
DIGIT=[0-9]([0-9']*[0-9])?
BYTE_UNIT=([KkMmGgTtPpEeZzYy][Ii]?[Bb])
INTEGER={SIGN}(0[xX][0-9a-fA-F]([0-9a-fA-F']*[0-9a-fA-F])?|0[bB][01]([01']*[01])?|{DIGIT}([ \t]?{BYTE_UNIT})?)
FLOAT={SIGN}(({DIGIT}\.[0-9]([0-9']*[0-9])?([eE]{SIGN}{DIGIT})?)|({DIGIT}[eE]{SIGN}{DIGIT})|[iI][nN][fF]|[nN][aA][nN])
BOOLEAN=([Yy][Ee][Ss]|[Nn][Oo]|[Oo][Nn]|[Oo][Ff][Ff]|[Ee][Nn][Aa][Bb][Ll][Ee][Dd]|[Dd][Ii][Ss][Aa][Bb][Ll][Ee][Dd]|[Tt][Rr][Uu][Ee]|[Ff][Aa][Ll][Ss][Ee])
DATE=[0-9]{4}-[0-9]{2}-[0-9]{2}
TIME=[Tt]?[0-9]{2}:[0-9]{2}(:[0-9]{2}(\.[0-9]+)?)?([Zz]|[+-][0-9]{2}:[0-9]{2})?
DATE_TIME={DATE}[Tt ]{TIME}
TIME_UNIT=([Nn][Aa][Nn][Oo][Ss][Ee][Cc][Oo][Nn][Dd][Ss]?|[Mm][Ii][Cc][Rr][Oo][Ss][Ee][Cc][Oo][Nn][Dd][Ss]?|[Mm][Ii][Ll][Ll][Ii][Ss][Ee][Cc][Oo][Nn][Dd][Ss]?|[Ss][Ee][Cc][Oo][Nn][Dd][Ss]?|[Mm][Ii][Nn][Uu][Tt][Ee][Ss]?|[Hh][Oo][Uu][Rr][Ss]?|[Dd][Aa][Yy][Ss]?|[Ww][Ee][Ee][Kk][Ss]?|[Mm][Oo][Nn][Tt][Hh][Ss]?|[Yy][Ee][Aa][Rr][Ss]?|[Nn][Ss]|[Uu][Ss]|µ[Ss]|[Mm][Ss]|[Ss]|[Mm]|[Hh]|[Dd]|[Ww])
TIME_DELTA={SIGN}{DIGIT}[ \t]?{TIME_UNIT}
DECORATION=-+

%%

<YYINITIAL> {
    {SPACE}                   { return TokenType.WHITE_SPACE; }
    {CRLF}                    { return ElclTypes.LINE_BREAK; }
    {COMMENT}                 { return ElclTypes.COMMENT; }
    {DECORATION}              { return ElclTypes.SECTION_DECORATION; }
    "*["                      { yybegin(SECTION); return ElclTypes.SECTION_LIST_OPEN; }
    "["                       { yybegin(SECTION); return ElclTypes.SECTION_MAP_OPEN; }
    {META_NAME}               { return ElclTypes.META_NAME; }
    "\"\"\""                    { yybegin(MULTI_TEXT); return ElclTypes.MULTILINE_TEXT_OPEN; }
    "```"                    { yybegin(MULTI_CODE); return ElclTypes.MULTILINE_CODE_OPEN; }
    "///"                    { yybegin(MULTI_REGEX); return ElclTypes.MULTILINE_REGEX_OPEN; }
    "<<<"                    { yybegin(MULTI_BYTES); return ElclTypes.MULTILINE_BYTES_OPEN; }
    {TEXT}                    { return ElclTypes.TEXT_NAME; }
    {CODE}                    { return ElclTypes.CODE; }
    {REGEX}                   { return ElclTypes.REGEX; }
    {BYTES}                   { return ElclTypes.BYTES; }
    {NAME}                    { return ElclTypes.NAME; }
    [: =]                     { yybegin(VALUE); return ElclTypes.ASSIGN; }
    ","                       { return ElclTypes.COMMA; }
    "*"                       { yybegin(VALUE); return ElclTypes.LIST_MARKER; }
    "."                       { return ElclTypes.DOT; }
}

<SECTION> {
    {SPACE}                   { return TokenType.WHITE_SPACE; }
    {TEXT}                    { return ElclTypes.TEXT_NAME; }
    {NAME}                    { return ElclTypes.NAME; }
    "."                       { return ElclTypes.DOT; }
    "]*"                      { yybegin(YYINITIAL); return ElclTypes.SECTION_LIST_CLOSE; }
    "]"                       { yybegin(YYINITIAL); return ElclTypes.SECTION_MAP_CLOSE; }
    {CRLF}                    { yybegin(YYINITIAL); return ElclTypes.LINE_BREAK; }
}

<VALUE> {
    {SPACE}                   { return TokenType.WHITE_SPACE; }
    {COMMENT}                 { return ElclTypes.COMMENT; }
    {CRLF}                    { yybegin(YYINITIAL); return ElclTypes.LINE_BREAK; }
    ","                       { return ElclTypes.COMMA; }
    "\"\"\""                    { yybegin(MULTI_TEXT); return ElclTypes.MULTILINE_TEXT_OPEN; }
    "```"                    { yybegin(MULTI_CODE); return ElclTypes.MULTILINE_CODE_OPEN; }
    "///"                    { yybegin(MULTI_REGEX); return ElclTypes.MULTILINE_REGEX_OPEN; }
    "<<<"                    { yybegin(MULTI_BYTES); return ElclTypes.MULTILINE_BYTES_OPEN; }
    {BOOLEAN}                 { return ElclTypes.BOOLEAN; }
    {DATE_TIME}               { return ElclTypes.DATE_TIME; }
    {DATE}                    { return ElclTypes.DATE; }
    {TIME}                    { return ElclTypes.TIME; }
    {FLOAT}                   { return ElclTypes.FLOAT; }
    {TIME_DELTA}              { return ElclTypes.TIME_DELTA; }
    {INTEGER}                 { return ElclTypes.INTEGER; }
    {TEXT}                    { return ElclTypes.TEXT; }
    {CODE}                    { return ElclTypes.CODE; }
    {REGEX}                   { return ElclTypes.REGEX; }
    {BYTES}                   { return ElclTypes.BYTES; }
    "*"                       { return ElclTypes.LIST_MARKER; }
}

<MULTI_TEXT> {
    [ \t]*"\"\"\""[ \t]*          { yybegin(VALUE); return ElclTypes.MULTILINE_TEXT_CLOSE; }
    {CRLF}                    { return ElclTypes.LINE_BREAK; }
    [^\r\n]+                 { return ElclTypes.MULTILINE_TEXT_CONTENT; }
}

<MULTI_CODE> {
    [ \t]*"```"[ \t]*          { yybegin(VALUE); return ElclTypes.MULTILINE_CODE_CLOSE; }
    {CRLF}                    { return ElclTypes.LINE_BREAK; }
    [^\r\n]+                 { return ElclTypes.MULTILINE_CODE_CONTENT; }
}

<MULTI_REGEX> {
    [ \t]*"///"[ \t]*          { yybegin(VALUE); return ElclTypes.MULTILINE_REGEX_CLOSE; }
    {CRLF}                    { return ElclTypes.LINE_BREAK; }
    [^\r\n]+                 { return ElclTypes.MULTILINE_REGEX_CONTENT; }
}

<MULTI_BYTES> {
    [ \t]*">>>"[ \t]*          { yybegin(VALUE); return ElclTypes.MULTILINE_BYTES_CLOSE; }
    {CRLF}                    { return ElclTypes.LINE_BREAK; }
    [^\r\n]+                 { return ElclTypes.MULTILINE_BYTES_CONTENT; }
}

[^]                           { return ElclTypes.BAD_CHARACTER; }
