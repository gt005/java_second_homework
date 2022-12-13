package DirectoryHandler;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DirectoryFilesConnector {
    private Path absolutePathToWorkDirectory;
    private List<Path> allDirectoryFilesPaths;
    private List<List<Integer>> allDirectoryFilesPathsLikeGraph;
    private int foundFilesPathsAmount;
    private final String regularExpressionForRequire;

    {
        regularExpressionForRequire = "(require ‘)(.*?)(’)";
    }

    /**
     * Ищет все файлы в заданной директории, ищет в файлах зависимости от других файлов и объединяет
     * в один результирующий файл.
     *
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

        prepareAndMakeTopologicalSort();
    }

    /**
     * Функция выводит список файлов и список файлов, которые были найдены в require внутри.
     */
    public void printFilesAndRelationsForTesting() {
        for (Path path : allDirectoryFilesPaths) {
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
     *
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

        foundFilesPathsAmount = result.size();

        return result;
    }

    /**
     * Находит в файле все упоминания require "filepath" и проверяет каждое упоминание на существование файла.
     *
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

    /**
     * Формирует направленный граф из списка путей, создает переменные, нужные для работы рекурсивной
     * сортировки topologicalSortRecursion и вызывает ее для сортировки.
     *
     * @return Список индексов исходного списка путей в правильном порядке
     */
    private List<Integer> prepareAndMakeTopologicalSort() {
        createGraphFromListOfPaths();  // Из списка путей у нас сформируется направленный граф

        // После сортировки, правильная последовательность будет в обратном порядке
        List<Integer> reversedRightSequenceOfNodes = new ArrayList<>();

        boolean[] visitedNodes = new boolean[foundFilesPathsAmount];
        for (int i = 0; i < foundFilesPathsAmount; i++) {
            visitedNodes[i] = false;
        }

        for (int i = 0; i < foundFilesPathsAmount; i++) {
            if (!visitedNodes[i]) {
                topologicalSortRecursion(i, visitedNodes, reversedRightSequenceOfNodes);
            }
        }

        List<Integer> rightSequenceOfNodes = new ArrayList<>();

        // Разворачиваем сортированный список
        for (int i = reversedRightSequenceOfNodes.size() - 1; i >= 0; i--) {
            rightSequenceOfNodes.add(reversedRightSequenceOfNodes.get(i));
        }

        return rightSequenceOfNodes;
    }

    /**
     * Рекурсивно выполняет топологическую сортировку для направленного графа.
     * Важно, что полученная последовательность развернута.
     *
     * @param currentNode          Индекс текущей просматриваемой вершины из массива путей (allDirectoryFilesPaths).
     * @param visitedNodes         Массив, в котором указаны статусы просмотра вершин
     * @param rightSequenceOfNodes Список, в котором будет формироваться правильная отсортированная последовательность.
     */
    private void topologicalSortRecursion(int currentNode, boolean[] visitedNodes, List<Integer> rightSequenceOfNodes) {
        visitedNodes[currentNode] = true;

        Iterator<Integer> graphIterator = allDirectoryFilesPathsLikeGraph.get(currentNode).iterator();
        int nextNodeForCheckIndex;

        // Для каждой смежной вершины, если она не проверена, вызвать проверку
        while (graphIterator.hasNext()) {
            nextNodeForCheckIndex = graphIterator.next();
            if (!visitedNodes[nextNodeForCheckIndex])
                topologicalSortRecursion(nextNodeForCheckIndex, visitedNodes, rightSequenceOfNodes);
        }

        rightSequenceOfNodes.add(currentNode);
    }

    /**
     * Создает граф из списка путей в виде двумерного списка чисел,
     * где каждое число - это индекс пути из списка allDirectoryFilesPaths.
     * Значение формируется в поле класса allDirectoryFilesPathsLikeGraph.
     */
    private void createGraphFromListOfPaths() {
        allDirectoryFilesPathsLikeGraph = new ArrayList<>();

        // Для i-того пути на i-ое место ставится список индексов, от которых он зависит
        for (int i = 0; i < foundFilesPathsAmount; ++i) {
            List<Path> correctPathsForFile;
            try {
                correctPathsForFile = findAllCorrectRequirePathsInFile(
                        allDirectoryFilesPaths.get(i)
                );
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage());
            }

            allDirectoryFilesPathsLikeGraph.add(new ArrayList<>());

            for (Path path : correctPathsForFile) {
                allDirectoryFilesPathsLikeGraph.get(i).add(
                        allDirectoryFilesPaths.indexOf(path)
                );  // Получаем индекс файла из исходного allDirectoryFilesPaths списка
            }
        }
    }
}
