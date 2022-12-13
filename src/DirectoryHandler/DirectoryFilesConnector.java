package DirectoryHandler;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DirectoryFilesConnector {
    private Path abstractPathToWorkDirectory;
    private List<Path> allDirectoryFilesPaths;

    /**
     * Ищет все файлы в заданной директории, ищет в файлах зависимости от других файлов и объединяет
     * в один результирующий файл.
     * @param directoryPath Директория, для которой искать и производить объединения.
     */
    public void findAndConnectFiles(String directoryPath) {
        abstractPathToWorkDirectory = Paths.get(directoryPath);

        if (!Files.exists(abstractPathToWorkDirectory)) {
            System.err.println("Неверный путь до папки.");
            return;
        }

        try {
            allDirectoryFilesPaths = getAllFilesFromDirectory();
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

        allDirectoryFilesPaths.forEach(System.out::println);
    }

    /**
     * Ищет все файлы в поддиректориях любой вложенности.
     * Директорией для поиска является поле класса abstractPathToWorkDirectory.
     * @return Список строк, содержащий абсолютные пути до найденных файлов
     */
    private List<Path> getAllFilesFromDirectory() throws IOException {
        List<Path> result;

        try (Stream<Path> walk = Files.walk(abstractPathToWorkDirectory)) {
            result = walk.filter(Files::isRegularFile).collect(Collectors.toList());
        } catch (IOException e) {
            throw new IOException("Не удалось произвести поиск файлов в директории");
        }

        return result;
    }
}
