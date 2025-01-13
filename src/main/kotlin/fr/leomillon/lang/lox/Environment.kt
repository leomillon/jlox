package fr.leomillon.lang.lox

class Environment(val enclosing: Environment? = null) {
  private val values = mutableMapOf<String, Any?>()

  fun define(name: String, value: Any?) {
    values[name] = value
  }

  operator fun get(name: Token): Any? =
    name.lexeme.let {
      values.getOrElse(it) {
        if (enclosing != null) {
          return enclosing[name]
        }

        throw RuntimeException("Undefined variable '$it'.")
      }
    }

  operator fun get(distance: Int, name: String): Any? =
    ancestor(distance).values[name]

  operator fun set(name: Token, value: Any?) {
    val lexeme = name.lexeme
    when {
      values.containsKey(lexeme) -> {
        values[lexeme] = value
      }

      enclosing != null -> {
        enclosing[name] = value
      }

      else -> {
        throw RuntimeException("Undefined variable '$lexeme'.")
      }
    }
  }

  operator fun set(distance: Int, name: Token, value: Any?) {
    ancestor(distance).values[name.lexeme] = value
  }

  private fun ancestor(distance: Int): Environment =
    generateSequence(this) { it.enclosing }
      .elementAt(distance)
}