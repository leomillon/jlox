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

class Parser(private val tokens: List<Token>) {
  private var current = 0

  class ParseError : RuntimeException()

  fun parse(): List<Stmt> {
    val statements = mutableListOf<Stmt>()
    while (!isAtEnd()) {
      declaration()?.also { statements.add(it) }
    }
    return statements
  }

  private fun declaration(): Stmt? {
    try {
      if (match(CLASS)) {
        return classDeclaration()
      }

      if (match(FUN)) {
        return function("function")
      }

      if (match(VAR)) {
        return varDeclaration()
      }

      return statement()
    } catch (error: ParseError) {
      synchronize()
      return null
    }
  }

  private fun classDeclaration(): Stmt {
    val name = consume(IDENTIFIER, "Expect class name.")

    val superclass = if (match(LESS)) {
      consume(IDENTIFIER, "Expect super class name.")
      Expr.Variable(previous())
    } else {
      null
    }

    consume(LEFT_BRACE, "Expect '{' before class body.")

    val methods = mutableListOf<Stmt.Function>()
    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      methods.add(function("method"))
    }
    consume(RIGHT_BRACE, "Expect '}' after class body.")

    return Stmt.Class(name, superclass, methods)
  }

  private fun function(kind: String): Stmt.Function {
    val name = consume(IDENTIFIER, "Expect $kind name.")

    consume(LEFT_PAREN, "Expect '(' after $kind name.")
    val params = mutableListOf<Token>()
    if (!check(RIGHT_PAREN)) {
      do {
        if (params.size >= 255) {
          logError(peek(), "Can't have more than 255 parameters.")
        }

        params.add(consume(IDENTIFIER, "Expect parameter name."))
      } while (match(COMMA))
    }

    consume(RIGHT_PAREN, "Expect ')' after parameters.")
    consume(LEFT_BRACE, "Expect '{' before $kind body.")
    return Stmt.Function(name, params, block())
  }

  private fun varDeclaration(): Stmt {
    val name = consume(IDENTIFIER, "Expect variable name.")

    val initializer = if (match(EQUAL)) {
      expression()
    } else {
      null
    }

    consume(SEMICOLON, "Expect ';' after variable declaration.")
    return Stmt.Var(name, initializer)
  }

  private fun statement(): Stmt {
    if (match(IF)) {
      return ifStatement()
    }

    if (match(PRINT)) {
      return printStatement()
    }

    if (match(RETURN)) {
      return returnStatement()
    }

    if (match(WHILE)) {
      return whileStatement()
    }

    if (match(FOR)) {
      return forStatement()
    }

    if (match(LEFT_BRACE)) {
      return Stmt.Block(block())
    }

    return expressionStatement()
  }

  private fun ifStatement(): Stmt {
    consume(LEFT_PAREN, "Expect '(' after 'if'.")
    val condition = expression()
    consume(RIGHT_PAREN, "Expect ')' after if condition.")
    val thenBranch = statement()
    val elseBranch = if (match(ELSE)) statement() else null
    return Stmt.If(condition, thenBranch, elseBranch)
  }

  private fun printStatement(): Stmt {
    val value = expression()
    consume(SEMICOLON, "Expect ';' after value.")
    return Stmt.Print(value)
  }

  private fun returnStatement(): Stmt {
    val keyword = previous()
    val value = if (!check(SEMICOLON)) {
      expression()
    } else {
      null
    }

    consume(SEMICOLON, "Expect ';' after return value      .")

    return Stmt.Return(keyword, value)
  }

  private fun whileStatement(): Stmt {
    consume(LEFT_PAREN, "Expect '(' after 'while'.")
    val condition = expression()
    consume(RIGHT_PAREN, "Expect ')' after while condition.")
    return Stmt.While(condition, statement())
  }

  private fun forStatement(): Stmt {
    consume(LEFT_PAREN, "Expect '(' after 'for'.")
    val initializer = when {
      match(SEMICOLON) -> null
      match(VAR) -> varDeclaration()
      else -> expressionStatement()
    }

    val condition = when {
      check(SEMICOLON) -> Expr.Literal(true)
      else -> expression()
    }
    consume(SEMICOLON, "Expect ';' after loop condition.")

    val increment = when {
      check(RIGHT_PAREN) -> null
      else -> expression()
    }
    consume(RIGHT_PAREN, "Expect ')' after loop clauses.")

    var body = statement()

    if (increment != null) {
      body = Stmt.Block(
        listOf(
          body,
          Stmt.Expression(increment)
        )
      )
    }

    body = Stmt.While(condition, body)

    if (initializer != null) {
      body = Stmt.Block(listOf(initializer, body))
    }

    return body
  }

  private fun block(): List<Stmt> {
    val statements = mutableListOf<Stmt>()
    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      declaration()?.also(statements::add)
    }
    consume(RIGHT_BRACE, "Expect '}' after block.")
    return statements
  }

  private fun expressionStatement(): Stmt {
    val expr = expression()
    consume(SEMICOLON, "Expect ';' after expression.")
    return Stmt.Expression(expr)
  }

  private fun expression(): Expr =
    assignment()

  private fun assignment(): Expr {
    val expr = or()

    if (match(EQUAL)) {
      val equals = previous()
      val value = assignment()

      if (expr is Expr.Variable) {
        return Expr.Assign(expr.name, value)
      }

      if (expr is Expr.Get) {
        return Expr.Set(expr.obj, expr.name, value)
      }

      logError(equals, "Invalid assignment target.")
    }

    return expr
  }

  private fun or(): Expr {
    var expr = and()

    while (match(OR)) {
      val operator = previous()
      val right = and()
      expr = Expr.Logical(expr, operator, right)
    }

    return expr
  }

  private fun and(): Expr {
    var expr = equality()

    while (match(AND)) {
      val operator = previous()
      val right = equality()
      expr = Expr.Logical(expr, operator, right)
    }

    return expr
  }

  private fun equality(): Expr {
    var expr = comparison()

    while (match(BANG_EQUAL, EQUAL_EQUAL)) {
      val operator = previous()
      val right = comparison()
      expr = Expr.Binary(expr, operator, right)
    }

    return expr
  }

  private fun comparison(): Expr {
    var expr = term()

    while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
      val operator = previous()
      val right = term()
      expr = Expr.Binary(expr, operator, right)
    }

    return expr
  }

  private fun term(): Expr {
    var expr = factor()

    while (match(MINUS, PLUS)) {
      val operator = previous()
      val right = factor()
      expr = Expr.Binary(expr, operator, right)
    }

    return expr
  }

  private fun factor(): Expr {
    var expr = unary()

    while (match(SLASH, STAR)) {
      val operator = previous()
      val right = unary()
      expr = Expr.Binary(expr, operator, right)
    }

    return expr
  }

  private fun unary(): Expr {
    if (match(BANG, MINUS)) {
      val operator = previous()
      val right = unary()
      return Expr.Unary(operator, right)
    }

    return call()
  }

  private fun call(): Expr {
    var expr = primary()

    while (true) {
      when {
        match(LEFT_PAREN) -> {
          expr = finishCall(expr)
        }

        match(DOT) -> {
          val propertyName = consume(IDENTIFIER, "Expect property name after '.'.")
          expr = Expr.Get(expr, propertyName)
        }

        else -> {
          break
        }
      }
    }

    return expr
  }

  private fun finishCall(expr: Expr): Expr {
    val arguments = mutableListOf<Expr>()

    if (!check(RIGHT_PAREN)) {
      do {
        if (arguments.size >= 255) {
          logError(peek(), "Can't have more than 255 arguments.")
        }
        arguments.add(expression())
      } while (match(COMMA))
    }

    val paren = consume(RIGHT_PAREN, "Expect ')' after arguments.")

    return Expr.Call(expr, paren, arguments)
  }

  private fun primary(): Expr =
    when {
      match(FALSE) -> Expr.Literal(false)
      match(TRUE) -> Expr.Literal(true)
      match(NIL) -> Expr.Literal(null)

      match(NUMBER, STRING) -> Expr.Literal(previous().literal)

      match(SUPER) -> {
        val keyword = previous()
        consume(DOT, "Expect '.' after 'super'.")
        val method = consume(IDENTIFIER, "Expect superclass method name.")
        Expr.Super(keyword, method)
      }

      match(THIS) -> Expr.This(previous())

      match(IDENTIFIER) -> Expr.Variable(previous())

      match(LEFT_PAREN) -> {
        val expr = expression()
        consume(RIGHT_PAREN, "Expect ')' after expression.")
        Expr.Grouping(expr)
      }

      else -> throw parseError(peek(), "Expect expression.")
    }

  private fun consume(type: TokenType, message: String): Token {
    if (check(type)) {
      return advance()
    }

    throw parseError(peek(), message)
  }

  private fun parseError(token: Token, message: String): ParseError {
    logError(token, message)
    return ParseError()
  }

  private fun logError(token: Token, message: String) {
    Lox.error(token, message)
  }

  private fun synchronize() {
    advance()

    while (!isAtEnd()) {
      if (previous().type == SEMICOLON) {
        return
      }

      when (peek().type) {
        CLASS,
        FUN,
        VAR,
        FOR,
        IF,
        WHILE,
        PRINT,
        RETURN,
          -> return

        else -> advance()
      }
    }
  }

  private fun match(vararg types: TokenType): Boolean {
    types.forEach { type ->
      if (check(type)) {
        advance()
        return true
      }
    }
    return false
  }

  private fun check(type: TokenType): Boolean {
    if (isAtEnd()) {
      return false
    }
    return peek().type == type
  }

  private fun isAtEnd(): Boolean =
    peek().type == EOF

  private fun peek(): Token =
    tokens[current]

  private fun advance(): Token {
    if (!isAtEnd()) {
      current++
    }
    return previous()
  }

  private fun previous(): Token =
    tokens[current - 1]
}