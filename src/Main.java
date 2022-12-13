import DirectoryHandler.DirectoryFilesConnector;

public class Main {
    public static void main(String[] args) {
        DirectoryFilesConnector taskDirectoryFilesConnector = new DirectoryFilesConnector();

        taskDirectoryFilesConnector.findAndConnectFiles("/Users/karimhamid/IdeaProjects/second_homework/folder_for_task");
        taskDirectoryFilesConnector.printFilesAndRelationsForTesting();
    }

}