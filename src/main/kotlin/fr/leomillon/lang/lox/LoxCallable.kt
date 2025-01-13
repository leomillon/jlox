package fr.leomillon.lang.lox

interface LoxCallable {
  fun arity(): Int

  fun call(interpreter: Interpreter, arguments: List<Any?>): Any?
}