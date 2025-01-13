package fr.leomillon.lang.lox

class LoxInstance(private val loxClass: LoxClass) {
  private val fields = mutableMapOf<String, Any?>()

  fun get(property: Token): Any? {
    if (fields.contains(property.lexeme)) {
      return fields[property.lexeme]
    }

    loxClass.findMethod(property.lexeme)
      ?.also { return it.bind(this) }

    throw RuntimeError(property, "Undefined property '${property.lexeme}'.")
  }

  fun set(property: Token, value: Any?) {
    fields[property.lexeme] = value
  }

  override fun toString() = "${loxClass.name} instance"
}
