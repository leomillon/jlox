package fr.leomillon.lang.lox

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class EnvironmentTest {
  @Test
  fun `should get var at distance`() {
    val rootEnv = Environment()
    val varName = "toto"
    rootEnv.define(varName, "hello")

    val subEnv = Environment(rootEnv)
    subEnv.define(varName, "world")

    val currentEnv = Environment(subEnv)

    assertThat(currentEnv[0, varName]).isNull()
    assertThat(currentEnv[1, varName]).isEqualTo("world")
    assertThat(currentEnv[2, varName]).isEqualTo("hello")
    assertFailure {
      assertThat(currentEnv[3, varName]).isNull()
    }
      .isInstanceOf<IndexOutOfBoundsException>()
  }
}