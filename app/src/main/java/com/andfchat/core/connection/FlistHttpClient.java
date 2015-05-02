package com.andfchat.core.connection;

import java.util.List;

import retrofit.http.Field;
import retrofit.http.FormUrlEncoded;
import retrofit.http.POST;
import retrofit.http.Path;
import retrofit.http.Query;

/**
 * Created by AndFChat on 06.04.2015.
 */
public interface FlistHttpClient {

    @FormUrlEncoded
    @POST("/json/getApiTicket.php")
    void logIn(@Field("account") String account, @Field("password") String password, retrofit.Callback<LoginData> callback);

    @POST("/json/api/bookmark-add.php")
    void addBookmark(@Query("account") String account, @Query("password") String password, @Query("name") String name, retrofit.Callback<String> callback);

    @POST("/json/api/bookmark-remove.php")
    void removeBookmark(@Query("account") String account, @Query("password") String password, @Query("name") String name, retrofit.Callback<String> callback);

    public class LoginData {
        private List<String> characters;

        private String ticket;
        private String error;
        private String default_character;
        private List<Friend> friends;
        private List<Bookmark> bookmarks;

        public List<String> getCharacters() {
            return characters;
        }

        public String getTicket() {
            return ticket;
        }

        public String getError() {
            return error;
        }

        public List<Friend> getFriends() {
            return friends;
        }

        public List<Bookmark> getBookmarks() {
            return bookmarks;
        }

        public String getDefaultCharacter() {
            return default_character;
        }

        public class Friend {
            private String source_name;
            private String dest_name;

            public String getFriend() {
                return source_name;
            }

            public String getCharacter() {
                return dest_name;
            }
        }

        public class Bookmark {
            private String name;

            public String getName() {
                return name;
            }
        }
    }
}