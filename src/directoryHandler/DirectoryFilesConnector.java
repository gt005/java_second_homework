package directoryHandler;

import java.io.FileWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Класс, который позволяет объединить все файлы в заданной директории и ее подкаталогах в один файл.
 * Чтобы файл из директории задал к себе зависимость другого файла, нужно прописать в нем запрос
 * в формате require ‘relative_filepath‘
 */
public class DirectoryFilesConnector {
    private final Path absolutePathToWorkDirectory;  // Директория, в которой будет производиться поиск файлов
    private List<Path> allDirectoryFilesPaths; // Все файлы в каталоге и подкаталогах, найденные в директории

    // Файлы представлены графом, в котором они представлены индексами из списка allDirectoryFilesPaths
    private List<List<Integer>> allDirectoryFilesPathsLikeGraph;
    private int foundFilesPathsAmount;  // Сколько файлов было найдено в каталоге и подкаталогах
    private final String regularExpressionForRequire;
    private int cycleStartIndexIfCycleWasFound;  // Сохраненный индекс первой вершины цикла графа из массива цикла
    private int cycleEndIndexIfCycleWasFound;    // Сохраненный индекс последней вершины цикла графа из массива цикла

    {
        regularExpressionForRequire = "(require ‘)(.*?)(’)";  // Строка вида "require ‘anything‘"
    }

    /**
     * Ищет все файлы в заданной директории, ищет в файлах зависимости от других файлов и объединяет
     * в один результирующий файл.
     *
     * @param directoryPath Директория, для которой искать и производить объединения.
     */
    public DirectoryFilesConnector(String directoryPath) {
        absolutePathToWorkDirectory = Paths.get(directoryPath);

        if (!Files.exists(absolutePathToWorkDirectory)) {
            System.err.println("Неверный путь до папки.");
            return;
        }

        try {
            allDirectoryFilesPaths = getAllFilesFromDirectory();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }

        createGraphFromListOfPaths();  // Из списка путей у нас сформируется направленный граф
        List<Integer> foundCycle = checkCyclesExistence();
        if (!foundCycle.isEmpty()) {
            StringJoiner errorCycleTraceback = new StringJoiner("\n");
            errorCycleTraceback.add("Найден цикл в зависимостях, из-за чего невозможно соединить файлы. Цикл файлов:");
            foundCycle.forEach((s) -> errorCycleTraceback.add(allDirectoryFilesPaths.get(s).toString()));

            throw new RuntimeException(errorCycleTraceback.toString());
        }

        List<Integer> sortedGraph = prepareAndMakeTopologicalSort();

        writeResultToFile(sortedGraph);
    }

    /**
     * Проверяет наличие циклов зависимостей между файлами по графу.
     * Вызывает рекурсивную вспомогательную функцию isCycleFound.
     *
     * @return Список индексов путей цикла. Либо пустой список, если циклов не обнаружено
     */
    private List<Integer> checkCyclesExistence() {
        int[] arrayForCycle = new int[foundFilesPathsAmount];
        int[] visitedNodes = new int[foundFilesPathsAmount];
        for (int i = 0; i < foundFilesPathsAmount; i++) {
            arrayForCycle[i] = -1;
            visitedNodes[i] = 0;
        }
        cycleStartIndexIfCycleWasFound = -1;

        for (int i = 0; i < foundFilesPathsAmount; ++i)
            if (isCycleFound(i, visitedNodes, arrayForCycle))
                break;

        if (cycleStartIndexIfCycleWasFound != -1) {
            List<Integer> cycle = new ArrayList<>();
            cycle.add(cycleStartIndexIfCycleWasFound);

            for (int i = cycleEndIndexIfCycleWasFound; i != cycleStartIndexIfCycleWasFound; i = arrayForCycle[i]) {
                cycle.add(i);
            }

            cycle.add(cycleStartIndexIfCycleWasFound);
            return cycle;
        }
        return new ArrayList<>();
    }

    /**
     * С помощью обхода в глубину ищет цикл в графе. Может найти любой из существующих цикл.
     *
     * @param currentNode   индекс текущей просматриваемой вершины
     * @param visitedNodes  Список просмотренных вершин
     * @param arrayForCycle Заполняет список индексами вершин, чтобы если нашелся цикл, его можно найти тут.
     * @return Если хоть один цикл найден, то true. Иначе false
     */
    private boolean isCycleFound(int currentNode, int[] visitedNodes, int[] arrayForCycle) {
        visitedNodes[currentNode] = 1;
        for (int i = 0; i < allDirectoryFilesPathsLikeGraph.get(currentNode).size(); ++i) {
            int nextNodeToCheck = allDirectoryFilesPathsLikeGraph.get(currentNode).get(i);
            if (visitedNodes[nextNodeToCheck] == 0) {
                arrayForCycle[nextNodeToCheck] = currentNode;

                if (isCycleFound(nextNodeToCheck, visitedNodes, arrayForCycle)) {
                    return true;
                }
            } else if (visitedNodes[nextNodeToCheck] == 1) {
                cycleEndIndexIfCycleWasFound = currentNode;
                cycleStartIndexIfCycleWasFound = nextNodeToCheck;
                return true;
            }
        }
        visitedNodes[currentNode] = 2;
        return false;
    }

    /**
     * Функция выводит список файлов и список файлов, которые были найдены в require внутри.
     */
    public void printFilesAndRelationsForTesting() {
        for (Path path : allDirectoryFilesPaths) {
            try {
                System.out.printf("Найти для файла %s\n", path.toString());
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
     * Создает переменные, нужные для работы рекурсивной
     * сортировки topologicalSortRecursion и вызывает ее для сортировки.
     *
     * @return Список индексов исходного списка путей в правильном порядке
     */
    private List<Integer> prepareAndMakeTopologicalSort() {
        List<Integer> rightSequenceOfNodes = new ArrayList<>();

        boolean[] visitedNodes = new boolean[foundFilesPathsAmount];
        for (int i = 0; i < foundFilesPathsAmount; i++) {
            visitedNodes[i] = false;
        }

        for (int i = 0; i < foundFilesPathsAmount; i++) {
            if (!visitedNodes[i]) {
                topologicalSortRecursion(i, visitedNodes, rightSequenceOfNodes);
            }
        }

        return rightSequenceOfNodes;
    }

    /**
     * Рекурсивно выполняет топологическую сортировку для направленного графа.
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

    /**
     * Записывает содержимое всех найденных файлов в файл answerFile.txt, который лежит в рабочей директории.
     * @param indexesOfFilesInOrder Порядок индексов файлов из allDirectoryFilesPaths, в котором записывать содержимое.
     */
    private void writeResultToFile(List<Integer> indexesOfFilesInOrder) {
        Path resultFilePath = Paths.get(absolutePathToWorkDirectory.toString(), "answerFile.txt");

        try (FileWriter resultFile = new FileWriter(resultFilePath.toFile(), false)) {
            for (Integer integer : indexesOfFilesInOrder) {
                resultFile.write(
                        Files.readString(allDirectoryFilesPaths.get(integer))
                );
            }
        } catch (IOException e) {
            throw new RuntimeException("Не удалось записать результат в файл.");
        }
    }
}
