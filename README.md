# jlox

Kotlin implementation of the jlox language described in the book "[Crafting Interpreters](http://craftinginterpreters.com/)".

## Installation

```shell
./gradlew installDist
```

## Usage

### REPL

#### Command

Run the following command:
```shell
./build/install/jlox/bin/jlox
```

#### Examples

```
1+5/2;
3.5
```

```
var name = "Léo";
print "Hello " + name;
Hello Léo
```

```
class Dummy { doSomething() { print super.someMethod(); } }
[line 1] Error at 'super': Can't use 'super' in a class with no superclass.
```

### Script

`/some/path/my-script.lox`
```
class Doughnut {
  cook() {
    print "Fry until golden brown.";
  }
}

class BostonCream < Doughnut {
  cook() {
    super.cook();
    print "Pipe full of custard and coat with chocolate.";
  }
}

BostonCream().cook();
```

Run the following command:
```shell
./build/install/jlox/bin/jlox /some/path/my-script.lox
```

Output:
```
Fry until golden brown.
Pipe full of custard and coat with chocolate.
```