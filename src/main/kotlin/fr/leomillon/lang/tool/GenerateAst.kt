package fr.leomillon.lang.tool

import java.io.Writer
import java.nio.file.Paths
import kotlin.io.path.writer
import kotlin.system.exitProcess

fun main(args: Array<String>) {
  if (args.size != 1) {
    System.err.println("Usage: generate_ast <output directory>")
    exitProcess(64)
  }
  val outputDir = args[0]
  defineAst(
    outputDir, "Expr", mapOf(
      "Assign" to "val name: Token, val value: Expr",
      "Binary" to "val left: Expr, val operator: Token, val right: Expr",
      "Call" to "val callee: Expr, val paren: Token, val arguments: List<Expr>",
      "Get" to "val obj: Expr, val name: Token",
      "Grouping" to "val expression: Expr",
      "Literal" to "val value: Any?",
      "Logical" to "val left: Expr, val operator: Token, val right: Expr",
      "Set" to "val obj: Expr, val name: Token, val value: Expr",
      "Super" to "val keyword: Token, val method: Token",
      "This" to "val keyword: Token",
      "Unary" to "val operator: Token, val right: Expr",
      "Variable" to "val name: Token",
    )
  )
  defineAst(
    outputDir, "Stmt", mapOf(
      "Block" to "val statements: List<Stmt>",
      "Class" to "val name: Token, val superclass: Expr.Variable?, val methods: List<Function>",
      "Expression" to "val expression: Expr",
      "Function" to "val name: Token, val params: List<Token>, val body: List<Stmt>",
      "If" to "val condition: Expr, val thenBranch: Stmt, val elseBranch: Stmt?",
      "Print" to "val expression: Expr",
      "Return" to "val keyword: Token, val value: Expr?",
      "Var" to "val name: Token, val initializer: Expr?",
      "While" to "val condition: Expr, val body: Stmt",
    )
  )
}

private fun defineAst(outputDir: String, baseName: String, fieldsByTypeName: Map<String, String>) {
  Paths.get(outputDir, "$baseName.kt")
    .writer()
    .use { writer ->
      writer.appendLine(
        """
        package fr.leomillon.lang.lox
        
        abstract class $baseName {
        """.trimIndent()
      )

      defineVisitor(writer, baseName, fieldsByTypeName.keys.sorted())
      writer.appendLine()

      fieldsByTypeName.forEach { (typeName, fields) ->
        writer.appendLine("\tclass $typeName($fields) : $baseName() {")
        writer.appendLine("\t\toverride fun <R> accept(visitor: Visitor<R>): R = visitor.visit$typeName$baseName(this)")
        writer.appendLine("\t}")
      }

      writer.appendLine()
      writer.appendLine("\tabstract fun <R> accept(visitor: Visitor<R>): R")

      writer.appendLine("}")
    }
}

private fun defineVisitor(writer: Writer, baseName: String, typeNames: List<String>) {
  writer.appendLine("\tinterface Visitor<R> {")
  typeNames.forEach { typeName ->
    writer.appendLine("\t\tfun visit$typeName$baseName(${baseName.lowercase()}: $typeName): R")
  }
  writer.appendLine("\t}")
}

