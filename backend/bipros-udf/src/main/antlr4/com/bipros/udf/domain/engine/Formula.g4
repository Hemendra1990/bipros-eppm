grammar Formula;

// Parser rules
expression
    : orExpr
    ;

orExpr
    : andExpr (OR andExpr)*
    ;

andExpr
    : comparisonExpr (AND comparisonExpr)*
    ;

comparisonExpr
    : additiveExpr ((EQ | NEQ | LT | GT | LTE | GTE) additiveExpr)?
    ;

additiveExpr
    : multiplicativeExpr ((PLUS | MINUS) multiplicativeExpr)*
    ;

multiplicativeExpr
    : unaryExpr ((MUL | DIV) unaryExpr)*
    ;

unaryExpr
    : MINUS unaryExpr
    | PLUS unaryExpr
    | NOT unaryExpr
    | primary
    ;

primary
    : functionCall
    | LPAREN expression RPAREN
    | variableRef
    | bracketRef
    | stringLiteral
    | numberLiteral
    | PI
    | EULER
    | TRUE
    | FALSE
    ;

functionCall
    : IF LPAREN expression COMMA expression COMMA expression RPAREN
    | MAX LPAREN expression (COMMA expression)* RPAREN
    | MIN LPAREN expression (COMMA expression)* RPAREN
    | ABS LPAREN expression RPAREN
    | ROUND LPAREN expression COMMA expression RPAREN
    | POWER LPAREN expression COMMA expression RPAREN
    | SQRT LPAREN expression RPAREN
    | SUM LPAREN expression (COMMA expression)* RPAREN
    | CONCAT LPAREN expression (COMMA expression)* RPAREN
    | LEFT LPAREN expression COMMA expression RPAREN
    | RIGHT LPAREN expression COMMA expression RPAREN
    | MID LPAREN expression COMMA expression COMMA expression RPAREN
    | LENGTH LPAREN expression RPAREN
    | UPPER LPAREN expression RPAREN
    | LOWER LPAREN expression RPAREN
    | TRIM LPAREN expression RPAREN
    | SUBSTITUTE LPAREN expression COMMA expression COMMA expression RPAREN
    | MOD LPAREN expression COMMA expression RPAREN
    | FLOOR LPAREN expression RPAREN
    | CEILING LPAREN expression RPAREN
    | LOG LPAREN expression RPAREN
    | EXP LPAREN expression RPAREN
    | SIN LPAREN expression RPAREN
    | COS LPAREN expression RPAREN
    | AVERAGE LPAREN expression (COMMA expression)* RPAREN
    | COUNT LPAREN expression (COMMA expression)* RPAREN
    | STDEV LPAREN expression (COMMA expression)* RPAREN
    | MEDIAN LPAREN expression (COMMA expression)* RPAREN
    | PERCENTILE LPAREN expression (COMMA expression)* RPAREN
    ;

variableRef
    : DOLLAR (IDENTIFIER | IF | MAX | MIN | ABS | ROUND | POWER | SQRT | SUM | CONCAT | AND | OR | NOT | LEFT | RIGHT | MID | LENGTH | UPPER | LOWER | TRIM | SUBSTITUTE | MOD | FLOOR | CEILING | LOG | EXP | SIN | COS | PI | EULER | TRUE | FALSE | AVERAGE | COUNT | STDEV | MEDIAN | PERCENTILE)
    ;

bracketRef
    : LBRACKET (IDENTIFIER | IF | MAX | MIN | ABS | ROUND | POWER | SQRT | SUM | CONCAT | AND | OR | NOT | LEFT | RIGHT | MID | LENGTH | UPPER | LOWER | TRIM | SUBSTITUTE | MOD | FLOOR | CEILING | LOG | EXP | SIN | COS | PI | EULER | TRUE | FALSE | AVERAGE | COUNT | STDEV | MEDIAN | PERCENTILE) RBRACKET
    ;

stringLiteral
    : STRING
    ;

numberLiteral
    : NUMBER
    ;

// Lexer rules
IF      : [Ii][Ff] ;
MAX     : [Mm][Aa][Xx] ;
MIN     : [Mm][Ii][Nn] ;
ABS     : [Aa][Bb][Ss] ;
ROUND   : [Rr][Oo][Uu][Nn][Dd] ;
POWER   : [Pp][Oo][Ww][Ee][Rr] ;
SQRT    : [Ss][Qq][Rr][Tt] ;
SUM     : [Ss][Uu][Mm] ;
CONCAT  : [Cc][Oo][Nn][Cc][Aa][Tt] ;
AND     : [Aa][Nn][Dd] ;
OR      : [Oo][Rr] ;
NOT     : [Nn][Oo][Tt] ;

// String functions
LEFT       : [Ll][Ee][Ff][Tt] ;
RIGHT      : [Rr][Ii][Gg][Hh][Tt] ;
MID        : [Mm][Ii][Dd] ;
LENGTH     : [Ll][Ee][Nn][Gg][Tt][Hh] ;
UPPER      : [Uu][Pp][Pp][Ee][Rr] ;
LOWER      : [Ll][Oo][Ww][Ee][Rr] ;
TRIM       : [Tt][Rr][Ii][Mm] ;
SUBSTITUTE : [Ss][Uu][Bb][Ss][Tt][Ii][Tt][Uu][Tt][Ee] ;

// Math functions
MOD        : [Mm][Oo][Dd] ;
FLOOR      : [Ff][Ll][Oo][Oo][Rr] ;
CEILING    : [Cc][Ee][Ii][Ll][Ii][Nn][Gg] ;
LOG        : [Ll][Oo][Gg] ;
EXP        : [Ee][Xx][Pp] ;
SIN        : [Ss][Ii][Nn] ;
COS        : [Cc][Oo][Ss] ;

// Constants
PI         : [Pp][Ii] ;
EULER      : [Ee] ;
TRUE       : [Tt][Rr][Uu][Ee] ;
FALSE      : [Ff][Aa][Ll][Ss][Ee] ;

// Statistical functions
AVERAGE    : [Aa][Vv][Ee][Rr][Aa][Gg][Ee] ;
COUNT      : [Cc][Oo][Uu][Nn][Tt] ;
STDEV      : [Ss][Tt][Dd][Ee][Vv] ;
MEDIAN     : [Mm][Ee][Dd][Ii][Aa][Nn] ;
PERCENTILE : [Pp][Ee][Rr][Cc][Ee][Nn][Tt][Ii][Ll][Ee] ;

LPAREN  : '(' ;
RPAREN  : ')' ;
LBRACKET: '[' ;
RBRACKET: ']' ;
COMMA   : ',' ;
DOLLAR  : '$' ;
PLUS    : '+' ;
MINUS   : '-' ;
MUL     : '*' ;
DIV     : '/' ;
EQ      : '=' | '==' ;
NEQ     : '!=' ;
LT      : '<' ;
GT      : '>' ;
LTE     : '<=' ;
GTE     : '>=' ;

NUMBER
    : '-'? [0-9]+ ('.' [0-9]+)?
    ;

STRING
    : '"' (~["\\\r\n] | '\\' .)* '"'
    | '\'' (~['\\\r\n] | '\\' .)* '\''
    ;

IDENTIFIER
    : [a-zA-Z_] [a-zA-Z0-9_]*
    ;

WS
    : [ \t\r\n]+ -> skip
    ;
