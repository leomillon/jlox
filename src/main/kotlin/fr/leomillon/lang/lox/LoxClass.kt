package fr.leomillon.lang.lox

import fr.leomillon.lang.lox.LoxFunction.Companion.INITIALIZER_NAME

class LoxClass(val name: String, val superclass: LoxClass?, val methods: Map<String, LoxFunction>) : LoxCallable {
  override fun arity() =
    findInitMethodOrNull()?.arity() ?: 0

  override fun call(interpreter: Interpreter, arguments: List<Any?>): LoxInstance {
    val instance = LoxInstance(this)
    findInitMethodOrNull()
      ?.also { initializer ->
        initializer.bind(instance).call(interpreter, arguments)
      }
    return instance
  }

  private fun findInitMethodOrNull() = findMethod(INITIALIZER_NAME)

  fun findMethod(name: String): LoxFunction? =
    methods[name] ?: superclass?.findMethod(name)

  override fun toString() = name
}
