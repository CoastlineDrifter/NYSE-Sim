package Banking;

public class Position {
    private int quantity;
    private double price;
    private String stock;
    private String status;

    public Position(int quantity, double price, String stockSymbol, String status) {
        this.quantity = quantity;
        this.price = price;
        this.stock = stock;
        this.status = status;
    }

    public void addPosition(int quantity, double price, String stock) {

    }

    public int getQuantity() {
        return quantity;
    }
    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
    public double getPrice() {
        return price;
    }
    public void setPrice(double price) {
        this.price = price;
    }
    public String getStock() {
        return stock;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    public void setQuantity(double quantity) {
        this.quantity = (int) quantity;
    }
    public void setPrice() {
        this.price = this.quantity * this.price;
    }
}
