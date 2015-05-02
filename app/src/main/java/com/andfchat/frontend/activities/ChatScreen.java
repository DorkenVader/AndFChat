/*******************************************************************************
 *     This file is part of AndFChat.
 *
 *     AndFChat is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     AndFChat is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with AndFChat.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/


package com.andfchat.frontend.activities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import net.sourcerer.quickaction.ActionItem;
import net.sourcerer.quickaction.PopUpAlignment;
import net.sourcerer.quickaction.QuickActionBar;
import net.sourcerer.quickaction.QuickActionOnClickListener;
import net.sourcerer.quickaction.QuickActionOnOpenListener;

import roboguice.RoboGuice;
import roboguice.activity.RoboFragmentActivity;
import roboguice.inject.InjectView;
import roboguice.util.Ln;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Point;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.andfchat.R;
import com.andfchat.core.connection.FlistWebSocketConnection;
import com.andfchat.core.data.CharacterManager;
import com.andfchat.core.data.Chatroom;
import com.andfchat.core.data.ChatroomManager;
import com.andfchat.core.data.SessionData;
import com.andfchat.core.data.history.HistoryManager;
import com.andfchat.core.data.messages.ChatEntry;
import com.andfchat.core.data.messages.ChatEntryFactory;
import com.andfchat.core.data.messages.ChatEntryFactory.AdClickListner;
import com.andfchat.core.util.SmileyReader;
import com.andfchat.core.util.Version;
import com.andfchat.frontend.application.AndFChatApplication;
import com.andfchat.frontend.application.AndFChatNotification;
import com.andfchat.frontend.events.AndFChatEventManager;
import com.andfchat.frontend.events.ChatroomEventListener;
import com.andfchat.frontend.events.ConnectionEventListener;
import com.andfchat.frontend.events.MessageEventListener;
import com.andfchat.frontend.events.UserEventListener;
import com.andfchat.frontend.fragments.ChannelListFragment;
import com.andfchat.frontend.fragments.ChatFragment;
import com.andfchat.frontend.fragments.ChatInputFragment;
import com.andfchat.frontend.fragments.UserListFragment;
import com.andfchat.frontend.menu.AboutAction;
import com.andfchat.frontend.menu.DisconnectAction;
import com.andfchat.frontend.menu.FriendListAction;
import com.andfchat.frontend.menu.JoinChannelAction;
import com.andfchat.frontend.popup.FListCharSelectionPopup;
import com.andfchat.frontend.popup.FListLoginPopup;
import com.andfchat.frontend.popup.FListPopupWindow;
import com.andfchat.frontend.util.Exporter;
import com.andfchat.frontend.util.FlistAlertDialog;
import com.google.inject.Inject;

public class ChatScreen extends RoboFragmentActivity implements ChatroomEventListener, AdClickListner, ConnectionEventListener {

    @Inject
    protected CharacterManager charManager;
    @Inject
    protected ChatroomManager chatroomManager;
    @Inject
    protected FlistWebSocketConnection connection;
    @Inject
    protected SessionData sessionData;
    @Inject
    private AndFChatEventManager eventManager;
    @Inject
    protected AndFChatNotification notificationManager;
    @Inject
    protected HistoryManager historyManager;
    @Inject
    protected ChatEntryFactory entryFactory;



    @InjectView(R.id.toggleSidebarLeft)
    private Button toggleSidebarLeft;
    @InjectView(R.id.toggleSidebarRight)
    private Button toggleSidebarRight;
    @InjectView(R.id.chatMessage)
    private EditText inputText;
    @InjectView(R.id.sendButton)
    private Button sendButton;
    @InjectView(R.id.actionButton)
    private Button actionButton;

    // Fragments
    private ChatFragment chat;
    private UserListFragment userList;
    private ChannelListFragment channelList;
    private ChatInputFragment inputFragment;

    // Display height
    private int height;
    private int width;

    private QuickActionBar actionBar;

    private FListLoginPopup loginPopup;
    private FListCharSelectionPopup charSelectionPopup;

    private boolean paused = false;
    private boolean onBack = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Register AdClickListner
        entryFactory.setAdClickListner(this);

        setTheme(sessionData.getSessionSettings().getTheme());
        setContentView(R.layout.activity_chat_screen);

        toggleSidebarRight.setSelected(true);

        eventManager.clear();
        eventManager.register((ChatroomEventListener) this);
        eventManager.register((ConnectionEventListener) this);

        // Fetch fragments
        chat = (ChatFragment)getSupportFragmentManager().findFragmentById(R.id.chatFragment);
        userList = (UserListFragment)getSupportFragmentManager().findFragmentById(R.id.userListFragment);
        channelList = (ChannelListFragment)getSupportFragmentManager().findFragmentById(R.id.channelListFragment);
        inputFragment = (ChatInputFragment)getSupportFragmentManager().findFragmentById(R.id.chatInputFragment);

        // Register fragments
        eventManager.register((ChatroomEventListener)chat);
        eventManager.register((MessageEventListener)chat);
        eventManager.register((ChatroomEventListener)userList);
        eventManager.register((UserEventListener)userList);
        eventManager.register(channelList);
        eventManager.register(inputFragment);

        // PopUps
        loginPopup = new FListLoginPopup();
        charSelectionPopup = new FListCharSelectionPopup();

        loginPopup.setDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (sessionData.getTicket() == null) {
                    openLogin();
                }
            }
        });

        charSelectionPopup.setDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (!sessionData.isInChat()) {
                    connection.closeConnection(ChatScreen.this);
                    sessionData.setTicket(null);
                    openLogin();
                }
            }
        });

        RoboGuice.injectMembers(this, charSelectionPopup);
        RoboGuice.injectMembers(this, loginPopup);

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        height = size.y;
        width = size.x;

        AndFChatApplication application = (AndFChatApplication)getApplicationContext();
        application.setScreenDimension(size);

        setupActionBar();
    }

    private void setupActionBar() {
        actionBar = new QuickActionBar(this);
        actionBar.setAlignment(PopUpAlignment.BOTTOM);

        //
        // Show description
        //
        ActionItem showDescprition = new ActionItem(getString(R.string.channel_description), getResources().getDrawable(R.drawable.ic_description));
        showDescprition.setQuickActionClickListener(new QuickActionOnClickListener() {

            @Override
            public void onClick(ActionItem item, View view) {
                showDescription();
            }
        });

        showDescprition.setQuickActionOnOpenListener(new QuickActionOnOpenListener() {

            @Override
            public void onOpen(ActionItem item) {
                Chatroom chat = chatroomManager.getActiveChat();

                if (chat.isPrivateChat() || chat.isSystemChat()) {
                    item.setVisibility(View.GONE);
                }
                else {
                    item.setVisibility(View.VISIBLE);
                }
            }
        });
        actionBar.addActionItem(showDescprition);

        //
        // Export active chat
        //
        ActionItem exportActiveChat = new ActionItem(getString(R.string.export_text), getResources().getDrawable(R.drawable.ic_export));
        exportActiveChat.setQuickActionClickListener(new QuickActionOnClickListener() {

            @Override
            public void onClick(ActionItem item, View view) {
                exportChat();
            }
        });

        exportActiveChat.setQuickActionOnOpenListener(new QuickActionOnOpenListener() {

            @Override
            public void onOpen(ActionItem item) {
                Chatroom chat = chatroomManager.getActiveChat();
                item.setEnabled(chat.isSystemChat() == false);
            }
        });
        actionBar.addActionItem(exportActiveChat);

        actionButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                actionBar.show(actionButton);
            }
        });

        //
        // Post Ad
        //
        ActionItem postAd = new ActionItem(getString(R.string.post_ad_text), getResources().getDrawable(R.drawable.ic_post_ad));
        postAd.setQuickActionClickListener(new QuickActionOnClickListener() {

            @Override
            public void onClick(ActionItem item, View view) {
                inputFragment.sendTextAsAd();
            }
        });

        postAd.setQuickActionOnOpenListener(new QuickActionOnOpenListener() {

            @Override
            public void onOpen(ActionItem item) {
                Chatroom chat = chatroomManager.getActiveChat();
                if(chat.isChannel()) {
                    item.setVisibility(View.VISIBLE);
                }
                else {
                    item.setVisibility(View.GONE);
                }
            }
        });
        actionBar.addActionItem(postAd);

        //
        // Post Ad
        //
        ActionItem showProfile = new ActionItem(getString(R.string.show_profile), getResources().getDrawable(R.drawable.ic_info));
        showProfile.setQuickActionClickListener(new QuickActionOnClickListener() {

            @Override
            public void onClick(ActionItem item, View view) {
                Chatroom chat = chatroomManager.getActiveChat();
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.f-list.net/c/" + chat.getCharacters().get(0).getName()));
                startActivity(browserIntent);
            }
        });

        //
        // Show Profile
        //
        showProfile.setQuickActionOnOpenListener(new QuickActionOnOpenListener() {

            @Override
            public void onOpen(ActionItem item) {
                Chatroom chat = chatroomManager.getActiveChat();
                if (chat.isPrivateChat()) {
                    item.setVisibility(View.VISIBLE);
                } else {
                    item.setVisibility(View.GONE);
                }
            }
        });
        actionBar.addActionItem(showProfile);

        //
        // Leave active chat
        //
        ActionItem leaveActiveChat = new ActionItem(getString(R.string.leave_channel), getResources().getDrawable(R.drawable.ic_leave));
        leaveActiveChat.setQuickActionClickListener(new QuickActionOnClickListener() {

            @Override
            public void onClick(ActionItem item, View view) {
                leaveActiveChat();
            }
        });

        leaveActiveChat.setQuickActionOnOpenListener(new QuickActionOnOpenListener() {

            @Override
            public void onOpen(ActionItem item) {
                Chatroom chat = chatroomManager.getActiveChat();
                if (chat.isCloseable()) {
                    item.setVisibility(View.VISIBLE);
                }
                else {
                    item.setVisibility(View.GONE);
                }
            }
        });
        actionBar.addActionItem(leaveActiveChat);
    }

    public void leaveActiveChat() {
        Chatroom activeChat = chatroomManager.getActiveChat();
        if (activeChat != null) {
            connection.leaveChannel(activeChat);
        }
    }

    public void toggleSidebarRight(View v) {
        toggleSidebarRight.setSelected(userList.toggleVisibility());
    }

    public void toggleSidebarLeft(View v) {
        toggleSidebarLeft.setSelected(!channelList.toggleVisibility());
    }

    public void showDescription() {
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View layout = inflater.inflate(R.layout.popup_description, null);

        int height = (int)(this.height * 0.8f);
        int width = (int)(this.width * 0.8f);

        final PopupWindow descriptionPopup = new FListPopupWindow(layout, width, height);
        descriptionPopup.showAtLocation(chat.getView(), Gravity.CENTER, 0, 0);

        final TextView descriptionText = (TextView)layout.findViewById(R.id.descriptionText);
        descriptionText.setText(SmileyReader.addSmileys(this, chatroomManager.getActiveChat().getDescription()));
        // Enable touching/clicking links in text
        descriptionText.setMovementMethod(LinkMovementMethod.getInstance());
    }

    @Override
    public void onEvent(Chatroom chatroom, ChatroomEventType type) {
        if (type == ChatroomEventType.ACTIVE) {
            actionButton.setVisibility(View.VISIBLE);

            if (chatroom.isSystemChat()) {
                toggleSidebarRight.setVisibility(View.GONE);
            } else {
                toggleSidebarRight.setVisibility(View.VISIBLE);

                if (chatroom.isPrivateChat()) {
                    toggleSidebarRight.setVisibility(View.GONE);
                }
            }

            if (chatroom.isPrivateChat() && chatroom.getRecipient().getStatusMsg() != null) {
                setChannelTitle(chatroom.getName() + " - " + chatroom.getRecipient().getStatusMsg());
            } else {
                setChannelTitle(chatroom.getName());
            }
        }
    }

    private void setChannelTitle(String name) {
        this.setTitle(name);
    }

    public void exportChat() {
        String filename =  "FListLog-" + chatroomManager.getActiveChat().getName() + "-" + System.currentTimeMillis() + ".txt";
        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(path, filename);

        try {
            path.mkdirs();

            OutputStream os = new FileOutputStream(file);
            os.write(Exporter.exportText(this, chatroomManager.getActiveChat()));
            os.close();

            ChatEntry entry = entryFactory.getNotation(charManager.findCharacter(CharacterManager.USER_SYSTEM), "Sucessfully exported to the download dictory, filename: " + filename);
            chatroomManager.addMessage(chatroomManager.getActiveChat(), entry);

            // Tell the media scanner about the new file so that it is
            // immediately available to the user.
            MediaScannerConnection.scanFile(this,
                    new String[] { file.toString() }, null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                @Override
                public void onScanCompleted(String path, Uri uri) {

                }
            });
        } catch (IOException e) {
            Ln.w("ExternalStorage", "Error writing " + file, e);
            // TODO: String!
            ChatEntry entry = entryFactory.getError(charManager.findCharacter(CharacterManager.USER_SYSTEM), "Can't write output, download directory doesn't exist!");
            chatroomManager.addMessage(chatroomManager.getActiveChat(), entry);
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        paused = false;
        sessionData.setIsVisible(true);

        // Check Version
        checkVersion();

        // Reload chat
        if (chatroomManager.getActiveChat() != null) {
            chatroomManager.setActiveChat(chatroomManager.getActiveChat());
        }
        else {
            actionButton.setVisibility(View.GONE);
        }

        if (!connection.isConnected()) {
            openLogin();
        }
    }

    private void checkVersion() {
        Version version = sessionData.getSessionSettings().getVersion();

        if (version.isLowerThan("0.2.2")) {
            Ln.i("Updating to version 0.2.2");

            historyManager.clearHistory(true);
            sessionData.getSessionSettings().setVersion("0.2.2");
        }
        if (version.isLowerThan("0.2.3")) {
            Ln.i("Updating to version 0.2.3");

            historyManager.clearHistory(true);
            sessionData.getSessionSettings().setVersion("0.2.3");
        }
    }

    @Override
    protected void onPause() {
        inputFragment.saveEntry();

        super.onPause();

        if (connection.isConnected()) {
            notificationManager.updateNotification(0);
        }

        paused = true;
    }

    @Override
    protected void onStop() {
        Ln.i("onStop");
        super.onStop();
        sessionData.setIsVisible(false);

        if (connection.isConnected()) {
            historyManager.saveHistory();
        }
    }

    @Override
    protected void onDestroy() {
        Ln.i("onDestroy");
        if (onBack == false) {
            Ln.i("Removing notifications");
            notificationManager.cancelAll();
        }

        onBack = false;
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_main_screen, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        // Do smaller chat height on displayed keyboard the height is determined by display size.
        switch (item.getItemId()) {
            case R.id.action_add_chat:
                JoinChannelAction.open(this, chat.getView());
                return true;
            case R.id.action_open_friendlist:
                FriendListAction.open(this, chat.getView());
                return true;
            case R.id.action_disconnect:
                DisconnectAction.disconnect(this);
                return true;
            case R.id.action_open_settings:
                startActivity(new Intent(this, Settings.class));
                return true;
            case R.id.action_about:
                AboutAction.open(this, chat.getView());
                return true;
            case R.id.action_exit:
                onQuit();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Hides the soft keyboard if user is touching anywhere else.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        View view = getCurrentFocus();
        boolean ret = super.dispatchTouchEvent(event);

        if (view instanceof EditText) {
            View w = getCurrentFocus();
            int scrcoords[] = new int[2];
            w.getLocationOnScreen(scrcoords);
            float x = event.getRawX() + w.getLeft() - scrcoords[0];
            float y = event.getRawY() + w.getTop() - scrcoords[1];

            if (event.getAction() == MotionEvent.ACTION_UP && (x < w.getLeft() || x >= w.getRight() || y < w.getTop() || y > w.getBottom()) ) {
                inputFragment.hideKeyboard();
                inputFragment.saveEntry();
            }
        }

        return ret;
    }

    public void onQuit() {
        FlistAlertDialog dialog = new FlistAlertDialog(this, getResources().getString(R.string.question_back)) {

            @Override
            public void onYes() {
                connection.closeConnection(ChatScreen.this);
                finish();
            }

            @Override
            public void onNo() {}
        };

        dialog.show();
    }

    @Override
    public void openAd(Spannable text) {
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View layout = inflater.inflate(R.layout.popup_description, null);

        int height = (int)(this.height * 0.8f);
        int width = (int)(this.width * 0.8f);

        final PopupWindow descriptionPopup = new FListPopupWindow(layout, width, height);
        descriptionPopup.showAtLocation(chat.getView(), Gravity.CENTER, 0, 0);

        final TextView descriptionText = (TextView)layout.findViewById(R.id.descriptionText);
        descriptionText.setText(text);
        // Enable touching/clicking links in text
        descriptionText.setMovementMethod(LinkMovementMethod.getInstance());
    }

    public void openLogin() {
        if (paused) {
            return;
        }

        if (sessionData.getTicket() == null) {
            if (!loginPopup.isShowing()) {
                loginPopup.show(getFragmentManager(), "login_fragment");
            }
        }
        else {
            connection.connect(true);

            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    if (connection.isConnected()) {
                        openSelection();
                    }
                    else {
                        sessionData.setTicket(null);
                        openLogin();
                    }
                }
            };

            new Handler(Looper.getMainLooper()).postDelayed(runnable, 1000);
        }
    }

    public void openSelection() {
        if (!charSelectionPopup.isShowing()) {
            charSelectionPopup.show(getFragmentManager(), "select_fragment");
        }
    }

    @Override
    public void onBackPressed() {
        onBack = true;
        super.onBackPressed();
    }

    @Override
    public void onEvent(ConnectionEventType type) {
        if (type == ConnectionEventType.CONNECTED) {
            if (loginPopup.isShowing()) {
                loginPopup.dismiss();
            }

            openSelection();
        }
        else if (type == ConnectionEventType.CHAR_CONNECTED) {
            if (charSelectionPopup.isShowing()) {
                charSelectionPopup.dismiss();
            }

            sessionData.setDisconnectReason(null);
        }
        else if (type == ConnectionEventType.DISCONNECTED) {
            Ln.i("Disconnected, clear interface!");

            historyManager.saveHistory();

            channelList.clear();
            userList.clear();
            chat.clear();

            actionButton.setVisibility(View.GONE);

            openLogin();
        }
    }
}