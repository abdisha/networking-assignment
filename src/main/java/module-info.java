module com.assignment.networkingassignment {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.assignment.networkingassignment to javafx.fxml;
    exports com.assignment.networkingassignment;
}