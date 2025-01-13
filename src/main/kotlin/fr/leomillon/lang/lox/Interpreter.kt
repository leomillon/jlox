package fr.leomillon.lang.lox

import fr.leomillon.lang.lox.LoxFunction.Companion.INITIALIZER_NAME
import fr.leomillon.lang.lox.TokenType.AND
import fr.leomillon.lang.lox.TokenType.BANG
import fr.leomillon.lang.lox.TokenType.BANG_EQUAL
import fr.leomillon.lang.lox.TokenType.EQUAL_EQUAL
import fr.leomillon.lang.lox.TokenType.GREATER
import fr.leomillon.lang.lox.TokenType.GREATER_EQUAL
import fr.leomillon.lang.lox.TokenType.LESS
import fr.leomillon.lang.lox.TokenType.LESS_EQUAL
import fr.leomillon.lang.lox.TokenType.MINUS
import fr.leomillon.lang.lox.TokenType.OR
import fr.leomillon.lang.lox.TokenType.PLUS
import fr.leomillon.lang.lox.TokenType.SLASH
import fr.leomillon.lang.lox.TokenType.STAR
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class Interpreter : Expr.Visitor<Any?>, Stmt.Visitor<Unit> {

  private val globals = Environment()
  private var environment = globals
  private val locals = mutableMapOf<Expr, Int>()

  init {
    globals.define("clock", object : LoxCallable {
      override fun arity() = 0

      override fun call(interpreter: Interpreter, arguments: List<Any?>) =
        System.currentTimeMillis().toDouble() / 1000.0

      override fun toString() = "<native fn>"
    })
  }

  fun interpret(statements: List<Stmt>) {
    try {
      val singleExpression = statements.singleOrNull()
        ?.let { it as? Stmt.Expression }
        ?.expression

      if (singleExpression != null) {
        println(stringify(evaluate(singleExpression)))
      } else {
        statements.forEach {
          execute(it)
        }
      }
    } catch (error: RuntimeError) {
      Lox.runtimeError(error)
    }
  }

  fun resolve(expr: Expr, depth: Int) {
    locals[expr] = depth
  }

  private fun execute(statement: Stmt) {
    statement.accept(this)
  }

  private fun stringify(value: Any?): String {
    if (value == null) {
      return "nil"
    }

    if (value is Double) {
      return value.toString().removeSuffix(".0")
    }

    return value.toString()
  }

  override fun visitBlockStmt(stmt: Stmt.Block) {
    executeBlock(stmt.statements, Environment(environment))
  }

  override fun visitClassStmt(stmt: Stmt.Class) {
    val superclass = stmt.superclass
      ?.let(::evaluate)
      ?.let {
        if (it !is LoxClass) {
          throw RuntimeError(stmt.superclass.name, "Superclass must be a class.")
        }
        it
      }

    environment.define(stmt.name.lexeme, null)

    superclass?.also {
      environment = Environment(environment)
      environment.define("super", it)
    }

    val methods = stmt
      .methods
      .associateBy({ it.name.lexeme }) {
        LoxFunction(it, environment, it.name.lexeme == INITIALIZER_NAME)
      }

    superclass?.also {
      environment = environment.enclosing!!
    }

    environment[stmt.name] = LoxClass(stmt.name.lexeme, superclass, methods)
  }

  fun executeBlock(statements: List<Stmt>, environment: Environment) {
    val previous = this.environment
    try {
      this.environment = environment

      statements.forEach { statement ->
        execute(statement)
      }
    } finally {
      this.environment = previous
    }
  }

  override fun visitExpressionStmt(stmt: Stmt.Expression) {
    evaluate(stmt.expression)
  }

  override fun visitFunctionStmt(stmt: Stmt.Function) {
    environment.define(stmt.name.lexeme, LoxFunction(stmt, environment, false))
  }

  override fun visitIfStmt(stmt: Stmt.If) {
    if (isTruthy(evaluate(stmt.condition))) {
      execute(stmt.thenBranch)
    } else {
      stmt.elseBranch?.also(::execute)
    }
  }

  override fun visitPrintStmt(stmt: Stmt.Print) {
    evaluate(stmt.expression)
      .also { println(stringify(it)) }
  }

  override fun visitReturnStmt(stmt: Stmt.Return) {
    throw Return(stmt.value?.let(::evaluate))
  }

  override fun visitVarStmt(stmt: Stmt.Var) {
    environment.define(
      stmt.name.lexeme,
      stmt.initializer?.let { evaluate(it) }
    )
  }

  override fun visitWhileStmt(stmt: Stmt.While) {
    while (isTruthy(evaluate(stmt.condition))) {
      execute(stmt.body)
    }
  }

  override fun visitAssignExpr(expr: Expr.Assign): Any? {
    val value = evaluate(expr.value)
    val distance = locals.get(expr)
    val name = expr.name
    if (distance != null) {
      environment[distance, name] = value
    } else {
      globals[name] = value
    }
    return value
  }

  override fun visitBinaryExpr(expr: Expr.Binary): Any? {
    val leftValue = evaluate(expr.left)
    val rightValue = evaluate(expr.right)

    return when (expr.operator.type) {
      GREATER -> {
        checkNumberOperands(expr.operator, leftValue, rightValue)
        leftValue > rightValue
      }

      GREATER_EQUAL -> {
        checkNumberOperands(expr.operator, leftValue, rightValue)
        leftValue >= rightValue
      }

      LESS -> {
        checkNumberOperands(expr.operator, leftValue, rightValue)
        leftValue < rightValue
      }

      LESS_EQUAL -> {
        checkNumberOperands(expr.operator, leftValue, rightValue)
        leftValue <= rightValue
      }

      EQUAL_EQUAL -> {
        isEqual(leftValue, rightValue)
      }

      BANG_EQUAL -> {
        !isEqual(expr.left, expr.right)
      }

      MINUS -> {
        checkNumberOperands(expr.operator, leftValue, rightValue)
        leftValue - rightValue
      }

      PLUS -> {
        when {
          leftValue is Double && rightValue is Double -> {
            leftValue + rightValue
          }

          leftValue is String && rightValue is String -> {
            leftValue + rightValue
          }

          else -> {
            throw RuntimeError(expr.operator, "Operands must be two numbers or two strings.")
          }
        }
      }

      SLASH -> {
        checkNumberOperands(expr.operator, leftValue, rightValue)
        leftValue / rightValue
      }

      STAR -> {
        checkNumberOperands(expr.operator, leftValue, rightValue)
        leftValue * rightValue
      }

      else -> {
        null
      }
    }
  }

  override fun visitCallExpr(expr: Expr.Call): Any? {
    val function = evaluate(expr.callee)

    val arguments = expr.arguments.map { evaluate(it) }

    if (function !is LoxCallable) {
      throw RuntimeError(expr.paren, "Can only call functions and classes.")
    }

    if (arguments.size != function.arity()) {
      throw RuntimeError(expr.paren, "Expect ${function.arity()} arguments but got ${arguments.size}.")
    }

    return function.call(this, arguments)
  }

  override fun visitGetExpr(expr: Expr.Get): Any? {
    val obj = evaluate(expr.obj)
    if (obj is LoxInstance) {
      return obj.get(expr.name)
    }

    throw RuntimeError(expr.name, "Only instances have properties.")
  }

  override fun visitGroupingExpr(expr: Expr.Grouping): Any? {
    return evaluate(expr.expression)
  }

  override fun visitLiteralExpr(expr: Expr.Literal): Any? =
    expr.value

  override fun visitLogicalExpr(expr: Expr.Logical): Any? {
    val left = evaluate(expr.left)

    when (expr.operator.type) {
      OR -> if (isTruthy(left)) {
        return left
      }

      AND -> if (!isTruthy(left)) {
        return left
      }

      else -> throw RuntimeError(expr.operator, "Expect logical operator 'or' or 'and'.")
    }

    return evaluate(expr.right)
  }

  override fun visitSetExpr(expr: Expr.Set): Any {
    val obj = evaluate(expr.obj)
    if (obj !is LoxInstance) {
      throw RuntimeError(expr.name, "Only instances have fields.")
    }

    return obj.set(expr.name, evaluate(expr.value))
  }

  override fun visitSuperExpr(expr: Expr.Super): Any {
    val distance = locals[expr]!!
    val superclass = environment[distance, "super"] as LoxClass
    val instance = environment[distance - 1, "this"] as LoxInstance
    val method = superclass.findMethod(expr.method.lexeme)
      ?: throw RuntimeError(
      expr.method,
      "Undefined property '${expr.method.lexeme}'."
    )
    return method.bind(instance)
  }

  override fun visitThisExpr(expr: Expr.This): Any? =
    lookUpVariable(expr.keyword, expr)

  override fun visitUnaryExpr(expr: Expr.Unary): Any? {
    val rightValue = evaluate(expr.right)
    return when (expr.operator.type) {
      BANG -> !(isTruthy(rightValue))
      MINUS -> {
        checkNumberOperand(expr.operator, rightValue)
        -rightValue
      }

      else -> null
    }
  }

  override fun visitVariableExpr(expr: Expr.Variable): Any? =
    lookUpVariable(expr.name, expr)

  private fun lookUpVariable(name: Token, expr: Expr): Any? {
    val distance = locals[expr]
    return if (distance != null) {
      environment[distance, name.lexeme]
    } else {
      globals[name]
    }
  }

  private fun isEqual(leftValue: Any?, rightValue: Any?): Boolean {
    if (leftValue == null || rightValue == null) {
      return true
    }
    return leftValue == rightValue
  }

  @OptIn(ExperimentalContracts::class)
  private fun checkNumberOperand(operator: Token, operand: Any?) {
    contract {
      returns() implies (operand is Double)
    }
    if (operand !is Double) {
      throw RuntimeError(operator, "Operand must be a number.")
    }
  }

  @OptIn(ExperimentalContracts::class)
  private fun checkNumberOperands(operator: Token, left: Any?, right: Any?) {
    contract {
      returns() implies (left is Double && right is Double)
    }
    if (left is Double && right is Double) {
      return
    }
    throw RuntimeError(operator, "Operands must be numbers.")
  }

  private fun evaluate(expr: Expr): Any? =
    expr.accept(this)

  private fun isTruthy(value: Any?): Boolean {
    if (value == null) {
      return false
    }
    if (value is Boolean) {
      return value
    }
    return true
  }
}