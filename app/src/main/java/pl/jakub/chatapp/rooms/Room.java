package pl.jakub.chatapp.rooms;

/**
 * Represents a simple room.
 *
 * @author Jakub Zelmanowicz
 */
public class Room {

    // Id of the room.
    private final String id;

    // Name of the room.
    private final String name;

    // Number of users in the room.
    private final int usersInRoom;

    public Room(String id, String name, int usersInRoom) {
        this.id = id;
        this.name = name;
        this.usersInRoom = usersInRoom;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getUsersInRoom() {
        return usersInRoom;
    }
}
