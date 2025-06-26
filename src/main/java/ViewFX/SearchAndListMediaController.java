package ViewFX;

import Control.WorkManager;
import Module.Book;
import Module.Films;
import Module.Genre;
import Module.Media;
import Module.Show;
import javafx.animation.TranslateTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.controlsfx.control.CheckComboBox;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.Comparator;

public class SearchAndListMediaController {

    private WorkManager workManager;

    @FXML private AnchorPane mainAnchorPane;
    @FXML private VBox contentArea; // Novo FXML ID para o VBox principal de conteúdo
    @FXML private AnchorPane filterSidebar;
    @FXML private Button toggleSidebarButton;
    @FXML private TableView<Media> mediaTableView;
    @FXML private TextField titleIsbnFilterField;
    @FXML private TextField yearFilterField;
    @FXML private CheckComboBox<String> genreFilterCheckComboBox;
    @FXML private TextField personFilterField;
    @FXML private CheckBox filterBookCheckBox;
    @FXML private CheckBox filterFilmCheckBox;
    @FXML private CheckBox filterShowCheckBox;
    @FXML private ToggleGroup sortToggleGroup;
    @FXML private RadioButton sortRatingDescRadio;
    @FXML private RadioButton sortRatingAscRadio;
    @FXML private RadioButton sortTitleAscRadio;

    private ObservableList<Media> masterMediaList = FXCollections.observableArrayList();
    private ObservableList<Media> filteredAndSortedMediaList = FXCollections.observableArrayList();

    // Define a largura da sidebar como uma constante
    private static final double SIDEBAR_WIDTH = 200.0;

    @FXML
    public void initialize() {
        this.workManager = new WorkManager();
        setupTableColumns();
        populateGenreCheckComboBox();
        loadAllMedia();

        // Configuração inicial para a sidebar direita: escondida por padrão
        filterSidebar.setTranslateX(SIDEBAR_WIDTH); // Empurra a sidebar completamente para a direita (fora da tela)
        AnchorPane.setRightAnchor(contentArea, 0.0); // Conteúdo principal ocupa toda a largura inicialmente
        toggleSidebarButton.setText("<"); // Texto do botão quando a sidebar está escondida (aponta para a esquerda para abrir)
        AnchorPane.setRightAnchor(toggleSidebarButton, 10.0); // Posição inicial do botão de toggle (próximo à borda direita da janela)

        applyFiltersAndSort(); // Aplica filtros iniciais (nenhum) e ordenação padrão
    }

    private void setupTableColumns() {
        TableColumn<Media, String> typeColumn = (TableColumn<Media, String>) mediaTableView.getColumns().get(1);
        typeColumn.setCellValueFactory(cellData -> {
            Media media = cellData.getValue();
            if (media instanceof Book) {
                return new SimpleStringProperty("Livro");
            } else if (media instanceof Films) {
                return new SimpleStringProperty("Filme");
            } else if (media instanceof Show) {
                return new SimpleStringProperty("Série");
            }
            return new SimpleStringProperty("Desconhecido");
        });

        TableColumn<Media, String> ratingColumn = (TableColumn<Media, String>) mediaTableView.getColumns().get(3);
        ratingColumn.setCellValueFactory(cellData -> {
            float avg = WorkManager.calculateAverage(cellData.getValue());
            return new SimpleStringProperty(String.format("%.1f ★", avg));
        });

        TableColumn<Media, String> genresColumn = (TableColumn<Media, String>) mediaTableView.getColumns().get(4);
        genresColumn.setCellValueFactory(cellData -> {
            List<Genre> genres = cellData.getValue().getGenres();
            String genresString = genres.stream()
                    .filter(Objects::nonNull)
                    .map(Genre::getGenre)
                    .collect(Collectors.joining(", "));
            return new SimpleStringProperty(genresString.isEmpty() ? "N/A" : genresString);
        });
    }

    private void populateGenreCheckComboBox() {
        List<String> genreNames = workManager.getGenres().stream()
                .map(Genre::getGenre)
                .collect(Collectors.toList());
        genreFilterCheckComboBox.getItems().setAll(genreNames);
    }

    private void loadAllMedia() {
        masterMediaList.setAll(workManager.listMediaAlphabetically());
        mediaTableView.setItems(masterMediaList);
    }

    @FXML
    private void handleApplyFilters() {
        applyFiltersAndSort();
    }

    @FXML
    private void handleClearFilters() {
        titleIsbnFilterField.clear();
        yearFilterField.clear();
        genreFilterCheckComboBox.getCheckModel().clearChecks();
        personFilterField.clear();
        filterBookCheckBox.setSelected(false);
        filterFilmCheckBox.setSelected(false);
        filterShowCheckBox.setSelected(false);
        sortTitleAscRadio.setSelected(true); // Default sort
        applyFiltersAndSort();
    }

    private void applyFiltersAndSort() {
        List<Media> currentList = workManager.listMediaAlphabetically();

        // 1. Filter by Title/ISBN
        String titleIsbnSearchTerm = titleIsbnFilterField.getText().trim().toLowerCase();
        if (!titleIsbnSearchTerm.isEmpty()) {
            currentList = currentList.stream()
                    .filter(media -> media.getTitle().toLowerCase().contains(titleIsbnSearchTerm) ||
                            (media instanceof Book && ((Book) media).getIsbn().toLowerCase().contains(titleIsbnSearchTerm)))
                    .collect(Collectors.toList());
        }

        // 2. Filter by Year
        String yearText = yearFilterField.getText().trim();
        if (!yearText.isEmpty()) {
            try {
                int year = Integer.parseInt(yearText);
                currentList = currentList.stream()
                        .filter(media -> media.getYearRelease() == year)
                        .collect(Collectors.toList());
            } catch (NumberFormatException e) {
                showAlert("Entrada Inválida", "O ano de lançamento deve ser um número válido.");
                yearFilterField.clear();
                return; // Stop processing if year is invalid
            }
        }

        // 3. Filter by Genre
        List<String> selectedGenreNames = genreFilterCheckComboBox.getCheckModel().getCheckedItems();
        if (!selectedGenreNames.isEmpty()) {
            currentList = currentList.stream()
                    .filter(media -> media.getGenres().stream()
                            .anyMatch(genre -> selectedGenreNames.contains(genre.getGenre())))
                    .collect(Collectors.toList());
        }

        // 4. Filter by Person (Author, Director, Cast)
        String personSearchTerm = personFilterField.getText().trim().toLowerCase();
        if (!personSearchTerm.isEmpty()) {
            currentList = currentList.stream()
                    .filter(media -> {
                        if (media instanceof Book) {
                            return ((Book) media).getAuthor().toLowerCase().contains(personSearchTerm);
                        } else if (media instanceof Films) {
                            Films film = (Films) media;
                            return film.getDirection().toLowerCase().contains(personSearchTerm) ||
                                    film.getCast().stream().anyMatch(castMember -> castMember.toLowerCase().contains(personSearchTerm));
                        } else if (media instanceof Show) {
                            Show show = (Show) media;
                            return show.getCast().stream().anyMatch(castMember -> castMember.toLowerCase().contains(personSearchTerm));
                        }
                        return false;
                    })
                    .collect(Collectors.toList());
        }

        // 5. Filter by Media Type (checkboxes)
        boolean filterBook = filterBookCheckBox.isSelected();
        boolean filterFilm = filterFilmCheckBox.isSelected();
        boolean filterShow = filterShowCheckBox.isSelected();

        if (filterBook || filterFilm || filterShow) { // Only apply type filter if at least one checkbox is selected
            currentList = currentList.stream()
                    .filter(media -> (filterBook && media instanceof Book) ||
                            (filterFilm && media instanceof Films) ||
                            (filterShow && media instanceof Show))
                    .collect(Collectors.toList());
        }

        // 6. Sort the filtered list
        Comparator<Media> comparator;
        if (sortRatingDescRadio.isSelected()) {
            comparator = Comparator.comparingDouble(WorkManager::calculateAverage).reversed();
        } else if (sortRatingAscRadio.isSelected()) {
            comparator = Comparator.comparingDouble(WorkManager::calculateAverage);
        } else { // Default to sortTitleAscRadio
            comparator = Comparator.comparing(Media::getTitle, String.CASE_INSENSITIVE_ORDER);
        }
        currentList.sort(comparator);

        filteredAndSortedMediaList.setAll(currentList);
        mediaTableView.setItems(filteredAndSortedMediaList);

        if (filteredAndSortedMediaList.isEmpty()) {
            showAlert("Nenhum Resultado", "Nenhuma mídia encontrada com os filtros e critérios de busca selecionados.");
        }
    }

    @FXML
    private void handleToggleSidebar() {
        TranslateTransition transition = new TranslateTransition(Duration.millis(300), filterSidebar);
        double currentTranslateX = filterSidebar.getTranslateX();

        if (currentTranslateX == 0) { // Sidebar está aberta (posicionada na borda direita, translateX 0)
            // Fecha a sidebar (move para a direita para fora da tela)
            transition.setToX(SIDEBAR_WIDTH);
            toggleSidebarButton.setText("<"); // Mudar o texto do botão para indicar abertura (aponta para a esquerda)
            AnchorPane.setRightAnchor(contentArea, 0.0); // Conteúdo principal volta a ocupar o espaço total
            AnchorPane.setRightAnchor(toggleSidebarButton, 10.0); // Botão move para a direita (fora da sidebar)
        } else { // Sidebar está fechada (posicionada fora da tela à direita, translateX = SIDEBAR_WIDTH)
            // Abre a sidebar (move para a esquerda para a tela)
            transition.setToX(0);
            toggleSidebarButton.setText(">"); // Mudar o texto do botão para indicar fechamento (aponta para a direita)
            AnchorPane.setRightAnchor(contentArea, SIDEBAR_WIDTH); // Conteúdo principal é empurrado para a esquerda pela sidebar
            AnchorPane.setRightAnchor(toggleSidebarButton, SIDEBAR_WIDTH + 10.0); // Botão move junto com a borda esquerda da sidebar
        }
        transition.play();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}