package fr.leomillon.lang.lox

import fr.leomillon.lang.lox.TokenType.AND
import fr.leomillon.lang.lox.TokenType.BANG
import fr.leomillon.lang.lox.TokenType.BANG_EQUAL
import fr.leomillon.lang.lox.TokenType.CLASS
import fr.leomillon.lang.lox.TokenType.COMMA
import fr.leomillon.lang.lox.TokenType.DOT
import fr.leomillon.lang.lox.TokenType.ELSE
import fr.leomillon.lang.lox.TokenType.EOF
import fr.leomillon.lang.lox.TokenType.EQUAL
import fr.leomillon.lang.lox.TokenType.EQUAL_EQUAL
import fr.leomillon.lang.lox.TokenType.FALSE
import fr.leomillon.lang.lox.TokenType.FOR
import fr.leomillon.lang.lox.TokenType.FUN
import fr.leomillon.lang.lox.TokenType.GREATER
import fr.leomillon.lang.lox.TokenType.GREATER_EQUAL
import fr.leomillon.lang.lox.TokenType.IDENTIFIER
import fr.leomillon.lang.lox.TokenType.IF
import fr.leomillon.lang.lox.TokenType.LEFT_BRACE
import fr.leomillon.lang.lox.TokenType.LEFT_PAREN
import fr.leomillon.lang.lox.TokenType.LESS
import fr.leomillon.lang.lox.TokenType.LESS_EQUAL
import fr.leomillon.lang.lox.TokenType.MINUS
import fr.leomillon.lang.lox.TokenType.NIL
import fr.leomillon.lang.lox.TokenType.NUMBER
import fr.leomillon.lang.lox.TokenType.OR
import fr.leomillon.lang.lox.TokenType.PLUS
import fr.leomillon.lang.lox.TokenType.PRINT
import fr.leomillon.lang.lox.TokenType.RETURN
import fr.leomillon.lang.lox.TokenType.RIGHT_BRACE
import fr.leomillon.lang.lox.TokenType.RIGHT_PAREN
import fr.leomillon.lang.lox.TokenType.SEMICOLON
import fr.leomillon.lang.lox.TokenType.SLASH
import fr.leomillon.lang.lox.TokenType.STAR
import fr.leomillon.lang.lox.TokenType.STRING
import fr.leomillon.lang.lox.TokenType.SUPER
import fr.leomillon.lang.lox.TokenType.THIS
import fr.leomillon.lang.lox.TokenType.TRUE
import fr.leomillon.lang.lox.TokenType.VAR
import fr.leomillon.lang.lox.TokenType.WHILE

class Scanner(private val source: String) {

  companion object {
    private val keywords = mapOf(
      "and" to AND,
      "class" to CLASS,
      "else" to ELSE,
      "false" to FALSE,
      "for" to FOR,
      "fun" to FUN,
      "if" to IF,
      "nil" to NIL,
      "or" to OR,
      "print" to PRINT,
      "return" to RETURN,
      "super" to SUPER,
      "this" to THIS,
      "true" to TRUE,
      "var" to VAR,
      "while" to WHILE,
    )
  }

  private val tokens = mutableListOf<Token>()
  private var start = 0
  private var current = 0
  private var line = 1

  fun scanTokens(): List<Token> {
    while (!isAtEnd()) {
      start = current
      scanToken()
    }

    tokens.add(Token(EOF, "", null, line))
    return tokens
  }

  private fun isAtEnd(): Boolean =
    current >= source.length

  private fun scanToken() {
    when (val char = advance()) {
      '(' -> addToken(LEFT_PAREN)
      ')' -> addToken(RIGHT_PAREN)
      '{' -> addToken(LEFT_BRACE)
      '}' -> addToken(RIGHT_BRACE)
      ',' -> addToken(COMMA)
      '.' -> addToken(DOT)
      '-' -> addToken(MINUS)
      '+' -> addToken(PLUS)
      ';' -> addToken(SEMICOLON)
      '*' -> addToken(STAR)
      '!' -> addToken(if (match('=')) BANG_EQUAL else BANG)
      '=' -> addToken(if (match('=')) EQUAL_EQUAL else EQUAL)
      '<' -> addToken(if (match('=')) LESS_EQUAL else LESS)
      '>' -> addToken(if (match('=')) GREATER_EQUAL else GREATER)
      '/' -> {
        if (match('/')) {
          // A comment goes until the end of the line.
          while (peek() != '\n' && !isAtEnd()) {
            advance()
          }
        } else {
          addToken(SLASH)
        }
      }

      ' ', '\r', '\t' -> {
        // Ignore whitespace.
      }

      '\n' -> line++

      '"' -> string()

      else -> {
        if (isDigit(char)) {
          number()
        } else if (isAlpha(char)) {
          identifier()
        } else {
          Lox.error(line, "Unexpected character.")
        }
      }
    }
  }

  private fun advance(): Char =
    source[current++]

  private fun addToken(tokenType: TokenType) {
    addToken(tokenType, null)
  }

  private fun addToken(tokenType: TokenType, literal: Any?) {
    tokens.add(Token(tokenType, source.substring(start, current), literal, line))
  }

  private fun match(expected: Char): Boolean {
    if (isAtEnd()) {
      return false
    }
    if (source[current] != expected) {
      return false
    }

    current++
    return true
  }

  private fun peek(): Char {
    if (isAtEnd()) {
      return Char.MIN_VALUE // '\0' in Java
    }
    return source[current]
  }

  private fun string() {
    while (peek() != '"' && !isAtEnd()) {
      if (peek() == '\n') {
        line++
      }
      advance()
    }
    if (isAtEnd()) {
      Lox.error(line, "Unterminated string.")
      return
    }

    // The closing '"'.
    advance()

    // Trim the surrounding quotes.
    val value = source.substring(start + 1, current - 1)
    addToken(STRING, value)
  }

  private fun isDigit(char: Char) = char in ('0'..'9')

  private fun number() {
    while (isDigit(peek())) {
      advance()
    }

    if (peek() == '.' && isDigit(peekNext())) {
      advance()
      while (isDigit(peek())) {
        advance()
      }
    }

    addToken(NUMBER, source.substring(start, current).toDouble())
  }

  private fun peekNext(): Char {
    if (current + 1 >= source.length) {
      return Char.MIN_VALUE
    }
    return source[current + 1]
  }

  private fun isAlpha(char: Char): Boolean =
    char in 'a'..'z' || char in 'A'..'Z' || char == '_'

  private fun identifier() {
    while (isAlphaNumeric(peek())) {
      advance()
    }

    val type = source.substring(start, current).let(keywords::get) ?: IDENTIFIER
    addToken(type)
  }

  private fun isAlphaNumeric(char: Char): Boolean =
    isAlpha(char) || isDigit(char)
}
