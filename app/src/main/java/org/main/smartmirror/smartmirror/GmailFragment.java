package org.main.smartmirror.smartmirror;

import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Base64;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GmailFragment extends Fragment {

    public static List<String> messageList = new ArrayList<>();
    public static TextView textViewTitle;
    public static TextView textViewTo;
    public static String mTo;
    public static TextView textViewFrom;
    public static String mFrom;
    public static TextView textViewSubject;
    public static String mSubject;
    public static ListView listViewBody;
    public static String mBody;
    GoogleAccountCredential mCredential;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    private static String PREF_ACCOUNT_NAME = "";

    //SCOPES - Note: When adding/deleting scopes, it is necessary to reauthorize by:
    //               1. Remove SmartMirror from Google Account by going to Connected Apps and Services
    //               2. Run Google Account Picker (Navin uses the OldCalendarFragment code)
    //Otherwise, changes won't take place.
    private static final String[] SCOPES = {
            GmailScopes.GMAIL_LABELS,
            GmailScopes.GMAIL_READONLY,
            GmailScopes.MAIL_GOOGLE_COM,
            GmailScopes.GMAIL_MODIFY,
            GmailScopes.GMAIL_INSERT
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.gmail_fragment, container, false);

        textViewTitle = (TextView)view.findViewById(R.id.gmailTitle);
        textViewTo = (TextView)view.findViewById(R.id.messageTo);
        textViewFrom = (TextView)view.findViewById(R.id.messageFrom);
        textViewSubject = (TextView)view.findViewById(R.id.messageSubject);
        listViewBody = (ListView) view.findViewById(R.id.messageBody);

        SharedPreferences settings = getActivity().getPreferences(Context.MODE_PRIVATE);

        PREF_ACCOUNT_NAME = Preferences.getUserAccountName();

        mCredential = GoogleAccountCredential.usingOAuth2(
                getActivity().getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff())
                .setSelectedAccountName(settings.getString(PREF_ACCOUNT_NAME, null));

        mCredential.setSelectedAccountName(PREF_ACCOUNT_NAME);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isGooglePlayServicesAvailable()) {
            messageList.clear();
            refreshResults();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != getActivity().RESULT_OK) {
                    isGooglePlayServicesAvailable();
                }
                break;
        }
    }

    private void refreshResults() {
        if (isDeviceOnline()) {
            new MakeRequestTask(mCredential).execute();
        }
    }

    /**
     * Checks whether the device currently has a network connection.
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date. Will
     * launch an error dialog for the user to update Google Play Services if
     * possible.
     * @return true if Google Play Services is available and up to
     *     date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        final int connectionStatusCode =
                GooglePlayServicesUtil.isGooglePlayServicesAvailable(getActivity());
        if (GooglePlayServicesUtil.isUserRecoverableError(connectionStatusCode)) {
            return false;
        } else if (connectionStatusCode != ConnectionResult.SUCCESS ) {
            return false;
        }
        return true;
    }

    /**
     * An asynchronous task that handles the Gmail API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    private class MakeRequestTask extends AsyncTask<Void, Void, List<String>> {
        private com.google.api.services.gmail.Gmail mService = null;
        private Exception mLastError = null;

        public MakeRequestTask(GoogleAccountCredential credential) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.gmail.Gmail.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("Gmail API Android Quickstart")
                    .build();
        }

        /**
         * Background task to call Gmail API.
         * @param params no parameters needed for this task.
         */
        @Override
        protected List<String> doInBackground(Void... params) {
            try {
                 return getDataFromApi();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        /**
         * Fetch a list of Gmail labels attached to the specified account.
         * @return List of Strings labels.
         * @throws IOException
         */
        private List<String> getDataFromApi() throws IOException {
            // Get the labels in the user's account.
            String user = "me";
            String query = "in:inbox is:unread";
            textViewTitle.setText("Inbox");

            ListMessagesResponse messageResponse =
                    mService.users().messages().list(user).setQ(query).setMaxResults(Long.valueOf(1)).execute();

            List<Message> messages = messageResponse.getMessages();

            for(Message message : messages){

                Message message2 = mService.users().messages().get(user, message.getId()).execute();

                int headerSize = message2.getPayload().getHeaders().size();
                //Get who message is from
                for(int i = 0; i < headerSize; i++ ) {
                    if (message2.getPayload().getHeaders().get(i).getName().toString().equals("From")) {
                        mFrom = new String(message2.getPayload().getHeaders().get(i).getValue().toString());
                    }
                }
                //Get subject of message
                for(int j = 0; j < headerSize; j++){
                    if(message2.getPayload().getHeaders().get(j).getName().toString().equals("Subject")){
                        mSubject = new String(message2.getPayload().getHeaders().get(j).getValue().toString());
                    }
                }
                //Get who message is to
                for(int k = 0; k < headerSize; k++){
                    if(message2.getPayload().getHeaders().get(k).getName().toString().equals(("To"))){
                        mTo = new String(message2.getPayload().getHeaders().get(k).getValue().toString());
                    }
                }

                //Get the body of the message
                byte[] bodyBytes = Base64.decodeBase64(message2.getPayload().getParts().get(0).getBody().getData().trim().toString()); // get body
                mBody = new String(bodyBytes, "UTF-8");
                messageList.add(mBody);
            }
            return messageList;
        }

        @Override
        protected void onPostExecute(List<String> output) {
            textViewTo.setText("To: " + mTo + "\n");
            textViewFrom.setText("From: " + mFrom + "\n");
            textViewSubject.setText("Subject: " + mSubject + "\n");
            ArrayAdapter<String> arrayAdapter =
                    new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, messageList);
            listViewBody.setAdapter(arrayAdapter);
        }
    }
}