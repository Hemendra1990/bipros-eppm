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
    ;

variableRef
    : DOLLAR (IDENTIFIER | IF | MAX | MIN | ABS | ROUND | POWER | SQRT | SUM | CONCAT | AND | OR | NOT)
    ;

bracketRef
    : LBRACKET (IDENTIFIER | IF | MAX | MIN | ABS | ROUND | POWER | SQRT | SUM | CONCAT | AND | OR | NOT) RBRACKET
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
