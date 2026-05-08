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
    : DOLLAR IDENTIFIER
    ;

bracketRef
    : LBRACKET IDENTIFIER RBRACKET
    ;

stringLiteral
    : STRING
    ;

numberLiteral
    : NUMBER
    ;

// Lexer rules
IF      : 'IF' | 'if' ;
MAX     : 'MAX' | 'max' ;
MIN     : 'MIN' | 'min' ;
ABS     : 'ABS' | 'abs' ;
ROUND   : 'ROUND' | 'round' ;
POWER   : 'POWER' | 'power' ;
SQRT    : 'SQRT' | 'sqrt' ;
SUM     : 'SUM' | 'sum' ;
CONCAT  : 'CONCAT' | 'concat' ;
AND     : 'AND' | 'and' ;
OR      : 'OR' | 'or' ;
NOT     : 'NOT' | 'not' ;

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
    : '"' (~["\r\n])* '"'
    | '\'' (~['\r\n])* '\''
    ;

IDENTIFIER
    : [a-zA-Z_] [a-zA-Z0-9_]*
    ;

WS
    : [ \t\r\n]+ -> skip
    ;
