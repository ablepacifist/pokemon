package pokemon.object;

public class PlayerItem {
    private int playerId;
    private String itemType;
    private int quantity;

    public PlayerItem() {}

    public PlayerItem(int playerId, String itemType, int quantity) {
        this.playerId = playerId;
        this.itemType = itemType;
        this.quantity = quantity;
    }

    public int getPlayerId() { return playerId; }
    public void setPlayerId(int playerId) { this.playerId = playerId; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
