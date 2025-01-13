package fr.leomillon.lang.lox

import fr.leomillon.lang.lox.Resolver.ClassType.CLASS
import fr.leomillon.lang.lox.Resolver.ClassType.NONE
import fr.leomillon.lang.lox.Resolver.ClassType.SUBCLASS
import java.util.Stack

class Resolver(
  private val interpreter: Interpreter,
) : Expr.Visitor<Unit>, Stmt.Visitor<Unit> {

  private enum class FunctionType {
    NONE,
    FUNCTION,
    INITIALIZER,
    METHOD
  }

  private enum class ClassType {
    NONE,
    CLASS,
    SUBCLASS
  }

  private val scopes = Stack<MutableMap<String, Boolean>>()
  private var currentFunction = FunctionType.NONE
  private var currentClass = NONE

  override fun visitAssignExpr(expr: Expr.Assign) {
    resolve(expr.value)
    resolveLocal(expr, expr.name)
  }

  override fun visitBinaryExpr(expr: Expr.Binary) {
    resolve(expr.left)
    resolve(expr.right)
  }

  override fun visitCallExpr(expr: Expr.Call) {
    resolve(expr.callee)
    expr.arguments.forEach(::resolve)
  }

  override fun visitGetExpr(expr: Expr.Get) {
    resolve(expr.obj)
  }

  override fun visitGroupingExpr(expr: Expr.Grouping) {
    resolve(expr.expression)
  }

  override fun visitLiteralExpr(expr: Expr.Literal) {
    // Nothing to do...
  }

  override fun visitLogicalExpr(expr: Expr.Logical) {
    resolve(expr.left)
    resolve(expr.right)
  }

  override fun visitSetExpr(expr: Expr.Set) {
    resolve(expr.obj)
    resolve(expr.value)
  }

  override fun visitSuperExpr(expr: Expr.Super) {
    when {
      currentClass == NONE -> Lox.error(expr.keyword, "Can't use 'super' outside of a class.")
      currentClass != SUBCLASS -> Lox.error(expr.keyword, "Can't use 'super' in a class with no superclass.")
    }
    resolveLocal(expr, expr.keyword)
  }

  override fun visitThisExpr(expr: Expr.This) {
    if (currentClass == NONE) {
      Lox.error(expr.keyword, "Can't use 'this' outside of a class.")
      return
    }
    resolveLocal(expr, expr.keyword)
  }

  override fun visitUnaryExpr(expr: Expr.Unary) {
    resolve(expr.right)
  }

  override fun visitVariableExpr(expr: Expr.Variable) {
    if (scopes.isNotEmpty() && scopes.peek()[expr.name.lexeme] == false) {
      Lox.error(expr.name, "Can't read local variable in its own initializer.")
    }

    resolveLocal(expr, expr.name)
  }

  private fun resolveLocal(expr: Expr, name: Token) {
    scopes
      .reversed()
      .withIndex()
      .forEach { (index, scope) ->
        if (scope.contains(name.lexeme)) {
          interpreter.resolve(expr, index)
          return
        }
      }
  }

  override fun visitBlockStmt(stmt: Stmt.Block) {
    beginScope()
    resolve(stmt.statements)
    endScope()
  }

  override fun visitClassStmt(stmt: Stmt.Class) {
    val enclosingClass = currentClass
    currentClass = CLASS

    declare(stmt.name)
    define(stmt.name)

    stmt.superclass?.also {
      if (stmt.name.lexeme == it.name.lexeme) {
        Lox.error(it.name, "Can't inherit from itself.")
      }
      currentClass = SUBCLASS
      resolve(it)

      beginScope()
      scopes.peek()["super"] = true
    }

    beginScope()
    scopes.peek()["this"] = true

    stmt.methods.forEach {
      val declaration = if (it.name.lexeme == LoxFunction.INITIALIZER_NAME) {
        FunctionType.INITIALIZER
      } else {
        FunctionType.METHOD
      }
      resolveFunction(it, declaration)
    }
    endScope()

    stmt.superclass?.also {
      endScope()
    }

    currentClass = enclosingClass
  }

  private fun beginScope() {
    scopes.push(mutableMapOf())
  }

  fun resolve(statements: List<Stmt>) {
    statements.forEach(::resolve)
  }

  private fun resolve(statement: Stmt) {
    statement.accept(this)
  }

  private fun resolve(expr: Expr) {
    expr.accept(this)
  }

  private fun endScope() {
    scopes.pop()
  }

  override fun visitExpressionStmt(stmt: Stmt.Expression) {
    resolve(stmt.expression)
  }

  override fun visitFunctionStmt(stmt: Stmt.Function) {
    declare(stmt.name)
    define(stmt.name)

    resolveFunction(stmt, FunctionType.FUNCTION)
  }

  private fun resolveFunction(stmt: Stmt.Function, type: FunctionType) {
    val enclosingFunction = currentFunction
    currentFunction = type
    beginScope()
    stmt.params.forEach { param ->
      declare(param)
      define(param)
    }
    resolve(stmt.body)
    endScope()
    currentFunction = enclosingFunction
  }

  override fun visitIfStmt(stmt: Stmt.If) {
    resolve(stmt.condition)
    resolve(stmt.thenBranch)
    stmt.elseBranch?.also(::resolve)
  }

  override fun visitPrintStmt(stmt: Stmt.Print) {
    resolve(stmt.expression)
  }

  override fun visitReturnStmt(stmt: Stmt.Return) {
    if (currentFunction == FunctionType.NONE) {
      Lox.error(stmt.keyword, "Can't return from top-level code.")
    }
    stmt.value?.also {
      if (currentFunction == FunctionType.INITIALIZER) {
        Lox.error(stmt.keyword, "Can't return a value from an initializer.")
      }
      resolve(it)
    }
  }

  override fun visitVarStmt(stmt: Stmt.Var) {
    declare(stmt.name)
    if (stmt.initializer != null) {
      resolve(stmt.initializer)
    }
    define(stmt.name)
  }

  private fun declare(name: Token) {
    if (scopes.isEmpty()) {
      return
    }

    val scope = scopes.peek()
    if (scope.contains(name.lexeme)) {
      Lox.error(name, "Already a variable with this name in this scope.")
    }
    scope[name.lexeme] = false
  }

  private fun define(name: Token) {
    if (scopes.isEmpty()) {
      return
    }

    val scope = scopes.peek()
    scope[name.lexeme] = true
  }

  override fun visitWhileStmt(stmt: Stmt.While) {
    resolve(stmt.condition)
    resolve(stmt.body)
  }
}