package fr.leomillon.lang.lox

import fr.leomillon.lang.lox.Lox.Companion.runFile
import fr.leomillon.lang.lox.Lox.Companion.runPrompt
import java.nio.file.Paths
import kotlin.io.path.readText
import kotlin.system.exitProcess

class Lox {
  companion object {

    private val interpreter = Interpreter()

    private var hadError = false
    private var hadRuntimeError = false

    fun runFile(filePath: String) {
      runInput(Paths.get(filePath).readText())
      if (hadError) {
        exitProcess(65)
      }
      if (hadRuntimeError) {
        exitProcess(70)
      }
    }

    fun runPrompt() {
      System.`in`.reader().buffered().use { reader ->
        while (true) {
          reader.readLine()?.run(::runInput) ?: break
          hadError = false
        }
      }
    }

    fun runInput(content: String) {
      exec(content)
    }

    private fun exec(source: String) {
      val statements = Scanner(source)
        .scanTokens()
        .let(::Parser)
        .parse()

      if (hadError) {
        return
      }

      Resolver(interpreter).resolve(statements)

      if (hadError) {
        return
      }

      interpreter.interpret(statements)
    }

    fun error(line: Int, message: String) {
      report(line, "", message)
    }

    private fun report(line: Int, where: String, message: String) {
      System.err.println("[line $line] Error$where: $message")
      hadError = true
    }

    fun error(token: Token, message: String) {
      if (token.type == TokenType.EOF) {
        report(token.line, " at end", message)
      } else {
        report(token.line, " at '${token.lexeme}'", message)
      }
    }

    fun runtimeError(error: RuntimeError) {
      System.err.println("${error.message}\n[line ${error.token.line}]")
      hadRuntimeError = true
    }
  }
}

fun main(args: Array<String>) {
  if (args.size > 1) {
    println("Usage: jlox [script]")
  } else if (args.size == 1) {
    runFile(args[0])
  } else {
    runPrompt()
  }
}
