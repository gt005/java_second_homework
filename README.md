### Задание 2 по предмету "Конструирование программного обеспечения" 2 курс.

Чтобы указать директорию для программы, в которой находить и работать с файлами, стоит создать объект класса DirectoryFilesConnector и указать абсолютный путь до директории.

Пример:
```java
new DirectoryFilesConnector(
    "/Users/karimhamid/IdeaProjects/second_homework/folder_for_task"
);
```

Есть дополнительная опция, если создавать объект в переменную, то можно вызвать метод, помогающий в тестировании.

Метод printFilesAndRelationsForTesting() выведет все найденные файлы и зависимости в них.
```java
DirectoryFilesConnector taskDirectoryFilesConnector = new DirectoryFilesConnector(
    "/Users/karimhamid/IdeaProjects/second_homework/folder_for_task"
);

taskDirectoryFilesConnector.printFilesAndRelationsForTesting();
```
---
Чтобы запустить программу, нужно указать в Main.java путь до нужной для работы директории и выполнить в терминале команды:
```shell
$ cd src/
$ javac Main.java
$ java Main
```
Для примера, можно воспользоваться папкой folder_for_task. Укажите абсолютный путь к ней и запустите программу.

<ins>Выполнены все условия реализации для решения полной задачи.</ins>