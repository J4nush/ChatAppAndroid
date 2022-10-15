package pl.jakub.chatapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import pl.jakub.chatapp.net.AsyncRequest;
import pl.jakub.chatapp.net.GetRequest;
import pl.jakub.chatapp.net.PostRequest;
import pl.jakub.chatapp.net.PutRequest;
import pl.jakub.chatapp.rooms.Room;
import pl.jakub.chatapp.rooms.RoomAdapter;
import pl.jakub.chatapp.viewmodel.UserViewModel;

/**
 * Main activity of the Chat Application.
 *
 * @author Jakub Zelmanowicz
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ChatAppActivity";

    /**
     * ViewModel managing the user's data.
     */
    private UserViewModel userViewModel;

    private EditText nameEditText;
    private TextView errTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        userViewModel = ViewModelProvider.AndroidViewModelFactory
                .getInstance(getApplication())
                .create(UserViewModel.class);

        nameEditText = findViewById(R.id.nameEditText);
        errTextView = findViewById(R.id.errTextView);

        // Setting Up Refreshing with Swipe
        SwipeRefreshLayout refreshLayout = findViewById(R.id.refreshLayout);
        refreshLayout.setOnRefreshListener(this::loadRooms);

        // Getting saved data
        SharedPreferences preferences = getSharedPreferences("_", MODE_PRIVATE);
        String connectedRoomId = preferences.getString("connected_room_id", null);
        String connectedRoomName = preferences.getString("connected_room_name", null);
        String connectedUserId = preferences.getString("connected_user_id", null);

        // Should the room be opened on app creation?
        if (connectedRoomId != null && connectedRoomName != null) {
            Log.d(TAG, "onCreate: User UUID " + connectedUserId);

            Intent chat = new Intent(MainActivity.this, ChatActivity.class);
            chat.putExtra("userId", connectedUserId);
            chat.putExtra("roomId", connectedRoomId);
            chat.putExtra("roomName", connectedRoomName);
            startActivity(chat);
        }
    }

    @Override
    protected void onResume() {
        this.loadRooms();
        super.onResume();
    }

    private void loadRooms() {
        AsyncTask.execute( () -> {
            Utils.checkInternetConnection(MainActivity.this,
                    suc -> {
                        errTextView.setVisibility(View.INVISIBLE);
                        initializeRooms();
                    },
                    fail -> {
                        errTextView.setText(getText(R.string.no_internet_conn_err));
                        errTextView.setVisibility(View.VISIBLE);
                    } );
        } );
    }

    private RecyclerView recyclerView;
    private RoomAdapter roomAdapter;
    private RecyclerView.LayoutManager layoutManager;

    // Download all the rooms from the Web Chat Server.
    private void initializeRooms() {
        String url = String.format("%s/api/v1/room", Constants.API_URL);
        AsyncRequest roomsReq = new GetRequest(url);
        roomsReq.setOnResponse( roomsRes -> {

            String id, name;
            int usersInRoom;

            List<Room> rooms = new ArrayList<>();

            try {
                JSONArray arr = new JSONArray(roomsRes.body().string());
                JSONObject obj;

                for (int i = 0 ; i < arr.length() ; i++) {
                    obj = arr.getJSONObject(i);
                    id = obj.getString("id");
                    name = obj.getString("name");
                    usersInRoom = obj.getJSONArray("users").length();
                    Log.d(TAG, "initializeRooms: Users in room: " + usersInRoom);
                    rooms.add(new Room(id, name, usersInRoom));
                }

            } catch (JSONException | IOException e) {
                e.printStackTrace();
            }

            // Display rooms in UI.
            runOnUiThread( () -> {
                recyclerView = findViewById(R.id.roomsRecyclerView);

                roomAdapter = new RoomAdapter(rooms);
                roomAdapter.setOnRoomSelect(this::connectToRoom);

                layoutManager = new GridLayoutManager(this, 2);
                recyclerView.setAdapter(roomAdapter);
                recyclerView.setLayoutManager(layoutManager);
            } );

        } );

        roomsReq.run();

    }

    /**
     * Connecting to the specific
     * room.
     *
     * @param room - room to join to.
     */
    private void connectToRoom(Room room) {

        String token = getSharedPreferences("_", MODE_PRIVATE)
                .getString("fcm_token", "");
        String name = nameEditText.getText().toString();

        // Creates a user on the backend side.
        AsyncRequest userReq = new PostRequest("http://54.38.53.128:5000/api/v1/user",
                "{\"name\": \"" + name + "\", \"token\": \"" + token + "\"}");
        userReq.setOnResponse( userRes -> {

            String userId = null;

            try {
                JSONObject userObj = new JSONObject(userRes.body().string());
                userId = userObj.getString("id");
                getSharedPreferences("_", MODE_PRIVATE).edit().putString("fcm_user_id", userId).apply();

                userViewModel.changeUuid(userId);
            } catch (IOException | JSONException ioException) {
                ioException.printStackTrace();
            }

            Log.d(TAG, "connectToRoom: " + userViewModel.getUuid().getValue());

            // Adds user to the room on backend side.
            String finalUserId = userId;
            String url = String.format("%s/api/v1/room/%s/%s", Constants.API_URL, userId, room.getId());
            AsyncRequest joinRequest = new PutRequest(url, "");
            joinRequest.setOnResponse(joinRes -> {

                // Saving the currently connected room in storage.
                getSharedPreferences("_", MODE_PRIVATE)
                        .edit()
                        .putString("connected_room_id", room.getId())
                        .putString("connected_room_name", room.getName())
                        .putString("connected_user_id", finalUserId)
                        .apply();

                Log.d(TAG, "connectToRoom: Data Saved");

                Intent chat = new Intent(MainActivity.this, ChatActivity.class);
                chat.putExtra("userId", userViewModel.getUuid().getValue());
                chat.putExtra("roomId", room.getId());
                chat.putExtra("roomName", room.getName());
                startActivity(chat);

            } );
            joinRequest.run();

        } );

        userReq.run();

    }


}