package com.github.robertberry.crossword_helper;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import com.github.robertberry.crossword_helper.lib.SearchTree;
import com.github.robertberry.crossword_helper.tasks.GenerateSearchTreeTask;
import com.github.robertberry.crossword_helper.tasks.ReadUKACDTask;
import com.github.robertberry.crossword_helper.tasks.SearchTask;
import com.github.robertberry.crossword_helper.util.Either;
import com.google.common.base.Optional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CrosswordHelper extends Activity {
    private static final String TAG = "MainActivity";

    private Optional<SearchTree> searchTree = Optional.absent();

    private ProgressDialog loadingDictionaryDialog;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        loadingDictionaryDialog = ProgressDialog.show(this, "", getString(R.string.loading_dictionary));

        InputStream ukacd = getResources().openRawResource(R.raw.ukacd);

        new ReadUKACDTask() {
            @Override
            protected void onPostExecute(Either<Throwable, Map<Integer, ArrayList<String>>> result) {
                if (result.isLeft()) {
                    Log.e(TAG, "Error loading dictionary: " + result.left().leftValue);
                } else {
                    Map<Integer, ArrayList<String>> dictionary = result.right().rightValue;
                    Integer size = 0;

                    for (ArrayList<String> wordList : dictionary.values()) {
                        size += wordList.size();
                    }

                    Log.i(TAG, "Finished loading dictionary with " + dictionary.size() +
                            " different lengths and a total of " + size + " words");

                    loadingDictionaryDialog.setMessage(getString(R.string.building_search_tree));

                    onLoadDictionary(dictionary);
                }
            }
        }.execute(ukacd);

        final EditText searchBox = (EditText) findViewById(R.id.query);

        searchBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    triggerSearch(searchBox);
                    return true;
                }

                return false;
            }
        });
    }

    public void showSearchResults(List<String> results) {
        if (results.isEmpty()) {
            results.add("No results");
        }

        final ListView resultView = (ListView) findViewById(R.id.search_results);

        final ArrayAdapter resultAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, results);
        resultView.setAdapter(resultAdapter);
    }

    public void triggerSearch(View view) {
        EditText queryEdit = (EditText) findViewById(R.id.query);
        final String query = queryEdit.getText().toString();

        if (searchTree.isPresent()) {
            Log.i(TAG, "Triggering search for query " + query);
            new SearchTask(searchTree.get()) {
                @Override
                protected void onPostExecute(Set<String> words) {
                    Log.i(TAG, "Search for " + query + " completed");
                    for (String word: words) {
                        Log.i(TAG, word);
                    }
                    showSearchResults(new ArrayList<String>(words));
                }
            }.execute(query);
        } else {
            Log.i(TAG, "Search tree has not been generated yet.");
        }
    }

    public void onLoadDictionary(Map<Integer, ArrayList<String>> dictionary) {
        new GenerateSearchTreeTask() {
            @Override
            protected void onPostExecute(SearchTree tree) {
                Log.i(TAG, "Finished generating search tree.");
                // This is OK and does not need synchronisation as the search tree is isolated to the UI thread
                searchTree = Optional.of(tree);

                loadingDictionaryDialog.hide();
            }
        }.execute(dictionary);
    }
}
