import directoryHandler.DirectoryFilesConnector;

public class Main {
    public static void main(String[] args) {
        DirectoryFilesConnector taskDirectoryFilesConnector = new DirectoryFilesConnector(
                "/Users/karimhamid/IdeaProjects/second_homework/folder_for_task"
        );

        taskDirectoryFilesConnector.printFilesAndRelationsForTesting();  // Чтобы распечатать все найденные зависимости
    }
}
