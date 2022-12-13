package DirectoryHandler;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DirectoryFilesConnector {
    private Path absolutePathToWorkDirectory;
    private List<Path> allDirectoryFilesPaths;
    private final String regularExpressionForRequire;

    {
        regularExpressionForRequire = "(require ‘)(.*?)(’)";
    }

    /**
     * Ищет все файлы в заданной директории, ищет в файлах зависимости от других файлов и объединяет
     * в один результирующий файл.
     * @param directoryPath Директория, для которой искать и производить объединения.
     */
    public void findAndConnectFiles(String directoryPath) {
        absolutePathToWorkDirectory = Paths.get(directoryPath);

        if (!Files.exists(absolutePathToWorkDirectory)) {
            System.err.println("Неверный путь до папки.");
            return;
        }

        try {
            allDirectoryFilesPaths = getAllFilesFromDirectory();
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

        for (Path path: allDirectoryFilesPaths) {
            try {
                System.out.printf("Найти для файла %s\n", path.toUri());
                findAllCorrectRequirePathsInFile(path).forEach(System.out::println);
                System.out.println("\n");
            } catch (IOException e) {
                throw new RuntimeException("Не удалось открыть файл.");
            }
        }

    }

    /**
     * Ищет все файлы в поддиректориях любой вложенности.
     * Директорией для поиска является поле класса abstractPathToWorkDirectory.
     * @return Список строк, содержащий абсолютные пути до найденных файлов.
     * @throws IOException Если в случае обхода директории произошла ошибка.
     */
    private List<Path> getAllFilesFromDirectory() throws IOException {
        List<Path> result;

        try (Stream<Path> walk = Files.walk(absolutePathToWorkDirectory)) {
            result = walk.filter(Files::isRegularFile)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IOException("Не удалось произвести поиск файлов в директории");
        }

        return result;
    }

    /**
     * Находит в файле все упоминания require "filepath" и проверяет каждое упоминание на существование файла.
     * @param fileToFindPaths путь к файлу, в котором искать require указания.
     * @return Список путей к существующим фалам, которые требуются входному файлу.
     * @throws IOException Если в случае открытия входного файла произошла ошибка.
     */
    private List<Path> findAllCorrectRequirePathsInFile(Path fileToFindPaths) throws IOException {
        List<Path> allCorrectPaths = new ArrayList<>();  // Все пути, которые содержаться в require

        String fileData = new String(Files.readAllBytes(fileToFindPaths));

        Matcher m = Pattern.compile(regularExpressionForRequire)
                .matcher(fileData);

        while (m.find()) {  // Выбрать все строки внутри require
            Path fullAbsolutePathToRequireFile = Paths.get(
                    String.valueOf(absolutePathToWorkDirectory),
                    m.group(2)  // Группа, содержащая путь
            );

            if (Files.exists(fullAbsolutePathToRequireFile)) {
                allCorrectPaths.add(fullAbsolutePathToRequireFile);
            }
        }

        return allCorrectPaths;
    }
}
