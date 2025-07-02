module org.example.nysesim {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires com.almasb.fxgl.all;
    requires java.desktop;
    requires org.jfree.jfreechart;
    requires io.fair_acc.chartfx;
    requires io.fair_acc.dataset;
    requires de.gsi.chartfx.chart;
    requires de.gsi.chartfx.dataset;

    opens org.example.nysesim to javafx.fxml;
    exports org.example.nysesim;
}