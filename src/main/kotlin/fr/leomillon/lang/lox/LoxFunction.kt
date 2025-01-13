package fr.leomillon.lang.lox

class LoxFunction(
  private val declaration: Stmt.Function,
  private val closure: Environment,
  private val isInitializer: Boolean,
) : LoxCallable {

  companion object {
    const val INITIALIZER_NAME = "init"
  }

  override fun arity() = declaration.params.size

  override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
    val environment = Environment(closure)
    declaration.params.forEachIndexed { index, param ->
      environment.define(param.lexeme, arguments[index])
    }
    try {
      interpreter.executeBlock(declaration.body, environment)
    } catch (returnValue: Return) {
      if (isInitializer) {
        return getThisRef()
      }
      return returnValue.value
    }

    if (isInitializer) {
      return getThisRef()
    }

    return null
  }

  private fun getThisRef() = closure[0, "this"]

  fun bind(instance: LoxInstance): LoxFunction {
    val environment = Environment(closure)
    environment.define("this", instance)
    return LoxFunction(declaration, environment, isInitializer)
  }

  override fun toString() =
    "<fn ${declaration.name.lexeme}>"
}