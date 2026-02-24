module com.assignment.networkingassignment {
    requires javafx.controls;
    requires javafx.fxml;
    requires static lombok;


    opens com.assignment.networkingassignment to javafx.fxml;
    exports com.assignment.networkingassignment;
}