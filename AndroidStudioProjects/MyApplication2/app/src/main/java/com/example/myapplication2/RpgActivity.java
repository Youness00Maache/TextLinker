package com.example.myapplication2;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.media.MediaPlayer;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class RpgActivity extends AppCompatActivity {

    private TextView narrativeText;
    private EditText playerInput; // Changed from TextInputEditText to EditText to match layout
    private Button submitButton;
    private ScrollView scrollView;
    private SpannableStringBuilder gameLog;
    private JSONObject worldState;
    private Executor executor;
    private Gson gson;
    private Map<String, MediaPlayer> audioCache;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_rpg);
            
            // Initialize UI components with null checks
            narrativeText = findViewById(R.id.narrativeText);
            playerInput = findViewById(R.id.playerInput); // This should now work with EditText
            submitButton = findViewById(R.id.submitButton);
            scrollView = findViewById(R.id.scrollView);
            
            // Initialize game state
            initializeGameState();
            
            // Set up button click listener
            if (submitButton != null) {
                submitButton.setOnClickListener(v -> {
                    try {
                        handlePlayerInput();
                    } catch (Exception e) {
                        Log.e("RpgActivity", "Error processing player input", e);
                        if (narrativeText != null) {
                            narrativeText.append("\n\nSomething went wrong. Please try again.");
                        }
                    }
                });
            }
            
            // Display initial narrative
            updateNarrativeDisplay();
        } catch (Exception e) {
            Log.e("RpgActivity", "Error initializing activity", e);
            // Show a toast message instead of crashing
            Toast.makeText(this, "Error starting game. Please restart the app.", Toast.LENGTH_LONG).show();
        }
    }

    // Add the missing methods here
    
    private void initializeGameState() {
        try {
            // Initialize executor for background tasks
            executor = Executors.newSingleThreadExecutor();
            
            // Initialize Gson for JSON serialization/deserialization
            gson = new GsonBuilder().create();
            
            // Initialize audio cache
            audioCache = new HashMap<>();
            
            // Initialize game log
            gameLog = new SpannableStringBuilder();
            
            // Initialize world state
            worldState = initializeWorldState();
            
            Log.d("RpgActivity", "Game state initialized successfully");
        } catch (Exception e) {
            Log.e("RpgActivity", "Error initializing game state", e);
            Toast.makeText(this, "Failed to initialize game state", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void handlePlayerInput() {
        if (playerInput == null || playerInput.getText() == null) {
            return;
        }
        
        String input = playerInput.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "Please enter an action", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Add player input to game log with green color
        appendToGameLog("\n\n> " + input, android.graphics.Color.GREEN);
        
        // Clear input field
        playerInput.setText("");
        
        // Process input
        processPlayerInput(input);
    }
    
    private void updateNarrativeDisplay() {
        try {
            // Initialize game log if it's null
            if (gameLog == null) {
                gameLog = new SpannableStringBuilder();
            }
            
            // Display welcome message if game log is empty
            if (gameLog.length() == 0) {
                String welcomeText = "=== Welcome to the World of Eldoria ===\n\n";
                try {
                    welcomeText += worldState.getJSONObject("memory").getString("narrative_context");
                } catch (JSONException e) {
                    welcomeText += "You are an adventurer seeking fortune and glory.";
                }
                welcomeText += "\n\nYou stand at the entrance to the village of Oakvale. The wooden gates are open, and you can see villagers bustling about, preparing for what appears to be a festival. A guard stands at attention by the gate, eyeing you curiously.";
                welcomeText += "\n\nWhat would you like to do?";
                
                appendToGameLog(welcomeText, android.graphics.Color.WHITE);
            }
            
            // Set text to narrative display
            if (narrativeText != null) {
                narrativeText.setText(gameLog);
                
                // Scroll to bottom
                if (scrollView != null) {
                    scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                }
            }
        } catch (Exception e) {
            Log.e("RpgActivity", "Error updating narrative display", e);
        }
    }
    
    private void initializeGame() {
        try {
            // Initialize world state
            worldState = initializeWorldState();

            // Display initial narrative
            String welcomeText = "=== Welcome to the World of Eldoria ===\n\n";
            welcomeText += worldState.getJSONObject("memory").getString("narrative_context");
            welcomeText += "\n\nYou stand at the entrance to the village of Oakvale. The wooden gates are open, and you can see villagers bustling about, preparing for what appears to be a festival. A guard stands at attention by the gate, eyeing you curiously.";
            welcomeText += "\n\nWhat would you like to do?";

            appendToGameLog(welcomeText, android.graphics.Color.WHITE);
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error initializing game", Toast.LENGTH_SHORT).show();
        }
    }

    private void processPlayerInput(final String input) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // Process input and get result
                    JSONObject result = gameLoop(worldState, input);

                    // Update world state
                    worldState = result.getJSONObject("world_state");

                    // Get output
                    JSONObject output = result.getJSONObject("output");
                    final String narrativeOutput = output.getString("narrative_text");

                    // Update UI on main thread
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Add narrative text to game log
                            appendToGameLog("\n" + narrativeOutput, android.graphics.Color.WHITE);

                            // Process NPC dialogues
                            try {
                                JSONArray dialogues = output.getJSONArray("npc_dialogues");
                                JSONArray audioUrls = output.getJSONArray("audio_urls");

                                for (int i = 0; i < dialogues.length(); i++) {
                                    JSONObject dialogue = dialogues.getJSONObject(i);
                                    String speaker = dialogue.getString("speaker");
                                    String text = dialogue.getString("text");

                                    // Add dialogue to game log
                                    appendToGameLog("\n" + speaker + ": \"" + text + "\"", android.graphics.Color.YELLOW);

                                    // Play audio if available
                                    if (i < audioUrls.length()) {
                                        JSONObject audio = audioUrls.getJSONObject(i);
                                        String audioUrl = audio.getString("audio_url");
                                        // In a real app, you would play the audio here
                                        // For this example, we'll just log it
                                        appendToGameLog("\n[Voice URL: " + audioUrl + "]", android.graphics.Color.GRAY);
                                    }
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(RpgActivity.this, "Error processing input", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    private void appendToGameLog(String text, int color) {
        SpannableString spannableString = new SpannableString(text);
        spannableString.setSpan(new ForegroundColorSpan(color), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        gameLog.append(spannableString);
        narrativeText.setText(gameLog);

        // Scroll to bottom
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    // RPG Engine Implementation

    private JSONObject initializeWorldState() {
        try {
            JSONObject state = new JSONObject();

            // Player
            JSONObject player = new JSONObject();
            player.put("name", "Adventurer");

            JSONObject stats = new JSONObject();
            stats.put("strength", 10);
            stats.put("dexterity", 10);
            stats.put("constitution", 10);
            stats.put("intelligence", 10);
            stats.put("wisdom", 10);
            stats.put("charisma", 10);
            player.put("stats", stats);

            JSONObject health = new JSONObject();
            health.put("current", 20);
            health.put("max", 20);
            player.put("health", health);

            JSONArray inventory = new JSONArray();
            inventory.put("torch");
            inventory.put("bedroll");
            inventory.put("rations (3)");
            player.put("inventory", inventory);

            JSONObject equipment = new JSONObject();
            equipment.put("weapon", "short sword");
            equipment.put("armor", "leather armor");
            equipment.put("accessory", "lucky coin");
            player.put("equipment", equipment);

            JSONArray abilities = new JSONArray();
            abilities.put("strike");
            abilities.put("dodge");
            abilities.put("persuade");
            player.put("abilities", abilities);

            state.put("player", player);

            // Location
            JSONObject location = new JSONObject();
            location.put("current", "village_entrance");
            JSONArray visited = new JSONArray();
            visited.put("village_entrance");
            location.put("visited", visited);
            state.put("location", location);

            // NPCs
            JSONObject npcs = new JSONObject();

            JSONObject guard = new JSONObject();
            guard.put("name", "Guard Harlon");
            guard.put("disposition", 50);
            guard.put("location", "village_entrance");
            guard.put("state", "on_duty");
            guard.put("dialogue_history", new JSONArray());
            npcs.put("village_guard", guard);

            JSONObject innkeeper = new JSONObject();
            innkeeper.put("name", "Mabel");
            innkeeper.put("disposition", 60);
            innkeeper.put("location", "village_inn");
            innkeeper.put("state", "working");
            innkeeper.put("dialogue_history", new JSONArray());
            npcs.put("innkeeper", innkeeper);

            state.put("npcs", npcs);

            // Quests
            JSONObject quests = new JSONObject();
            JSONObject missingShipment = new JSONObject();
            missingShipment.put("name", "The Missing Shipment");
            missingShipment.put("description", "Find the merchant's missing supplies");
            missingShipment.put("status", "available");

            JSONObject objectives = new JSONObject();
            JSONObject talkToMerchant = new JSONObject();
            talkToMerchant.put("description", "Speak with Merchant Galen");
            talkToMerchant.put("completed", false);
            objectives.put("talk_to_merchant", talkToMerchant);

            JSONObject findShipment = new JSONObject();
            findShipment.put("description", "Locate the missing supplies");
            findShipment.put("completed", false);
            objectives.put("find_shipment", findShipment);

            missingShipment.put("objectives", objectives);
            quests.put("missing_shipment", missingShipment);
            state.put("quests", quests);

            // World
            JSONObject world = new JSONObject();
            world.put("time", "morning");
            JSONArray events = new JSONArray();
            events.put("Village festival preparations underway");
            world.put("events", events);

            JSONObject flags = new JSONObject();
            flags.put("village_gates_open", true);
            flags.put("bandits_nearby", true);
            world.put("flags", flags);
            state.put("world", world);

            // Memory
            JSONObject memory = new JSONObject();
            JSONArray recentEvents = new JSONArray();
            recentEvents.put("Arrived at the village");
            memory.put("recent_events", recentEvents);
            memory.put("important_decisions", new JSONArray());
            memory.put("narrative_context", "You are an adventurer who has just arrived at the village of Oakvale, seeking fortune and glory.");
            state.put("memory", memory);

            return state;
        } catch (JSONException e) {
            e.printStackTrace();
            return new JSONObject();
        }
    }

    private JSONObject process_player_input(String rawInput) {
        try {
            JSONObject result = new JSONObject();

            // Normalize input
            String input = rawInput.trim().toLowerCase();

            // Check for empty input
            if (input.isEmpty()) {
                result.put("valid", false);
                result.put("action", JSONObject.NULL);
                result.put("error", "Please enter an action for your character.");
                return result;
            }

            // Check for meta commands or GM instructions
            String[] metaPatterns = {
                "^as the (gm|game master|dm|dungeon master).*",
                "^change (the world|the setting|the scene).*",
                "^create (a|an) (npc|character|monster).*",
                "^add (a|an) (item|weapon|spell).*",
                "^teleport.*",
                "^spawn.*",
                "^set.*",
                "^modify.*"
            };

            for (String pattern : metaPatterns) {
                if (input.matches(pattern)) {
                    result.put("valid", false);
                    result.put("action", JSONObject.NULL);
                    result.put("error", "Please only describe actions your character would take, not meta-game instructions.");
                    return result;
                }
            }

            // Categorize the action
            String actionType = "other";

            if (input.matches("^(go|walk|run|move|travel|head|enter|exit|leave).*")) {
                actionType = "movement";
            } else if (input.matches("^(attack|fight|strike|slash|stab|shoot|cast|use).*")) {
                actionType = "combat";
            } else if (input.matches("^(talk|speak|ask|tell|say|greet|call).*")) {
                actionType = "dialogue";
            } else if (input.matches("^(take|grab|pick|get|collect|steal|loot).*")) {
                actionType = "item_interaction";
            } else if (input.matches("^(look|examine|inspect|search|investigate|check).*")) {
                actionType = "observation";
            } else if (input.matches("^(help|what can i do|options|actions|commands).*")) {
                actionType = "help";
            }

            result.put("valid", true);
            result.put("action", input);
            result.put("type", actionType);

            return result;
        } catch (JSONException e) {
            e.printStackTrace();
            try {
                JSONObject errorResult = new JSONObject();
                errorResult.put("valid", false);
                errorResult.put("action", JSONObject.NULL);
                errorResult.put("error", "Error processing input.");
                return errorResult;
            } catch (JSONException ex) {
                return new JSONObject();
            }
        }
    }

    // Corresponds to the "generate_narrative" function definition
    private JSONObject generate_narrative(JSONObject worldState, JSONObject playerAction) {
        try {
            JSONObject result = new JSONObject();
            String narrativeText = "";
            JSONArray npcDialogues = new JSONArray();
            JSONArray narrativeEvents = new JSONArray();

            // Handle help requests
            if (playerAction.getString("type").equals("help")) {
                narrativeText = "As an adventurer in this world, you can:\n" +
                    "- Move around (go north, enter the cave, etc.)\n" +
                    "- Talk to characters (talk to guard, ask innkeeper about rumors)\n" +
                    "- Interact with items (take sword, examine chest)\n" +
                    "- Fight enemies (attack goblin, defend against wolf)\n" +
                    "- Observe your surroundings (look around, inspect wall)\n\n" +
                    "What would you like to do?";
                narrativeEvents.put("Player requested help");

                result.put("narrative_text", narrativeText);
                result.put("npc_dialogues", npcDialogues);
                result.put("narrative_events", narrativeEvents);
                return result;
            }

            // Get current location details
            String currentLocation = worldState.getJSONObject("location").getString("current");

            // Handle different action types
            String actionType = playerAction.getString("type");
            String action = playerAction.getString("action");

            switch (actionType) {
                case "movement":
                    // Example movement handling
                    if (currentLocation.equals("village_entrance") && action.contains("inn")) {
                        narrativeText = "You make your way through the bustling village square, passing by merchants setting up their stalls for the festival. The wooden sign of 'The Prancing Pony' inn swings gently in the breeze as you approach.";
                        narrativeEvents.put("Moved from village_entrance to village_inn");
                    } else if (currentLocation.equals("village_entrance") && action.contains("market")) {
                        narrativeText = "You walk toward the market district, where colorful tents and stalls are being prepared for the upcoming festival. The air is filled with the scent of fresh bread and spices.";
                        narrativeEvents.put("Moved from village_entrance to village_market");
                    } else {
                        narrativeText = "You look around, but don't see a clear path in that direction. The village entrance has paths leading to the inn to the north and the market to the east.";
                    }
                    break;

                case "dialogue":
                    // Example dialogue handling
                    if (currentLocation.equals("village_entrance") && action.contains("guard")) {
                        narrativeText = "You approach Guard Harlon, who stands at attention by the village gate.";

                        JSONObject dialogue1 = new JSONObject();
                        dialogue1.put("speaker", "Guard Harlon");
                        dialogue1.put("text", "Welcome to Oakvale, traveler. Here for the festival, are you? Keep your nose clean while you're here.");
                        dialogue1.put("voice_profile", "npc_male"); // Added voice profile
                        npcDialogues.put(dialogue1);

                        narrativeEvents.put("Spoke with Guard Harlon");

                        // If this is the first conversation, guard might mention the quest
                        JSONArray dialogueHistory = worldState.getJSONObject("npcs").getJSONObject("village_guard").getJSONArray("dialogue_history");
                        boolean mentionedMerchant = false;

                        for (int i = 0; i < dialogueHistory.length(); i++) {
                            if (dialogueHistory.getString(i).equals("mentioned_merchant")) {
                                mentionedMerchant = true;
                                break;
                            }
                        }

                        if (!mentionedMerchant) {
                            JSONObject dialogue2 = new JSONObject();
                            dialogue2.put("speaker", "Guard Harlon");
                            dialogue2.put("text", "If you're looking for work, you might want to speak with Merchant Galen. He's been fretting about a missing shipment all morning. You'll find him at the market.");
                            dialogue2.put("voice_profile", "npc_male"); // Added voice profile
                            npcDialogues.put(dialogue2);

                            narrativeEvents.put("Guard mentioned merchant's missing shipment");
                        }
                    } else if (currentLocation.equals("village_entrance") && action.contains("innkeeper")) {
                        narrativeText = "There's no innkeeper here at the village entrance. You'll need to head to the inn first.";
                    } else {
                        narrativeText = "There's no one here who matches that description.";
                    }
                    break;

                case "observation":
                    // Example observation handling
                    if (currentLocation.equals("village_entrance")) {
                        narrativeText = "The village of Oakvale welcomes you with its open wooden gates. Guards stand at attention, eyeing visitors with cautious gazes. Colorful banners hang from buildings, announcing the upcoming harvest festival. To the north, you can see the village inn, and to the east lies the market district. Villagers bustle about, preparing for the festivities.";
                        narrativeEvents.put("Observed village_entrance");
                    }
                    break;

                default:
                    narrativeText = "You consider your options. The village entrance has paths leading to the inn to the north and the market to the east. Guard Harlon stands vigilantly by the gate.";
            }

            result.put("narrative_text", narrativeText);
            result.put("npc_dialogues", npcDialogues); // These now contain voice_profile
            result.put("narrative_events", narrativeEvents);

            return result;
        } catch (JSONException e) {
            e.printStackTrace();
            try {
                JSONObject errorResult = new JSONObject();
                errorResult.put("narrative_text", "Something went wrong with the narrative generation.");
                errorResult.put("npc_dialogues", new JSONArray());
                errorResult.put("narrative_events", new JSONArray());
                return errorResult;
            } catch (JSONException ex) {
                return new JSONObject();
            }
        }
    }

    private JSONObject update_world_state(JSONObject currentState, JSONArray narrativeEvents) {
        try {
            JSONObject state = new JSONObject(currentState.toString()); // Deep copy to avoid modifying original

            // Process narrative events
            for (int i = 0; i < narrativeEvents.length(); i++) {
                String event = narrativeEvents.getString(i);

                // --- Existing Movement Logic ---
                if (event.startsWith("Moved from")) {
                    String[] parts = event.split(" to ");
                    if (parts.length == 2) {
                        String newLocation = parts[1];
                        state.getJSONObject("location").put("current", newLocation);
                        // Reset interaction flags for the new location if needed
                        JSONObject npcs = state.getJSONObject("npcs");
                        // Correct way to get keys from a JSONObject
                        JSONArray npcIds = npcs.names(); // Use instance method names()
                        if (npcIds != null) { // names() can return null if the object is empty
                            for (int j = 0; j < npcIds.length(); j++) {
                                String npcId = npcIds.getString(j);
                                JSONObject npc = npcs.getJSONObject(npcId);
                                if (npc.getString("location").equals(newLocation)) {
                                    // Reset flags or update status based on location change if necessary
                                }
                            }
                        }
                    }
                } else if (event.equals("Guard mentioned merchant's missing shipment")) {
                    // Update NPC state to remember this interaction
                    state.getJSONObject("npcs").getJSONObject("village_guard").getJSONArray("dialogue_history").put("mentioned_merchant");
                    // Update quest status if needed (already correctly placed here)
                    if (state.getJSONObject("quests").getJSONObject("missing_shipment").getString("status").equals("available")) {
                        state.getJSONObject("quests").getJSONObject("missing_shipment").put("status", "known");
                    }
                }
                // --- Existing Guard Spoke Logic ---
                else if (event.equals("Spoke with Guard Harlon")) {
                    // Potentially update general interaction count or specific flags
                    // Example: Increment interaction count
                    JSONObject guard = state.getJSONObject("npcs").getJSONObject("village_guard");
                    int interactions = guard.optInt("interaction_count", 0);
                    guard.put("interaction_count", interactions + 1);
                }
                // Add more event processing logic here

                // --- Add World Event Update Logic Inside Loop ---
                // Add event to world events if significant
                if (event.startsWith("Moved") || event.startsWith("Spoke") ||
                    event.startsWith("Completed") || event.startsWith("Found")) {
                    // Ensure the "world" and "events" structure exists before trying to put
                    if (!state.has("world")) state.put("world", new JSONObject());
                    if (!state.getJSONObject("world").has("events")) state.getJSONObject("world").put("events", new JSONArray());
                    state.getJSONObject("world").getJSONArray("events").put(event); // Use 'state' and 'event'
                }
                // --- End World Event Update Logic ---

            } // End of the for loop

            // --- Ensure the misplaced/redundant block AFTER this loop is DELETED ---
            /*
            // DELETE THIS BLOCK: Handle quest updates
            if (event.equals("Guard mentioned merchant's missing shipment")) {
                // Update NPC dialogue history
                updatedState.getJSONObject("npcs").getJSONObject("village_guard").getJSONArray("dialogue_history").put("mentioned_merchant");

                // Update quest status if needed
                if (updatedState.getJSONObject("quests").getJSONObject("missing_shipment").getString("status").equals("available")) {
                    updatedState.getJSONObject("quests").getJSONObject("missing_shipment").put("status", "known");
                }
            }

            // Add event to world events if significant
            if (event.startsWith("Moved") || event.startsWith("Spoke") || ...) {
                updatedState.getJSONObject("world").getJSONArray("events").put(event);
            }
            */
            // --- End of deletion area ---

            return state; // Return the modified state

        } catch (JSONException e) {
            e.printStackTrace();
            // Return original state on error to prevent losing data
            // Use the input parameter 'currentState'
            return currentState;
        }
    }

    private JSONObject synthesize_voice(String text, String voiceProfile, String ttsEndpoint) { // Added ttsEndpoint parameter
        try {
            JSONObject result = new JSONObject();

            // Validate voice profile against allowed enum values
            List<String> allowedProfiles = List.of("narrator", "npc_male", "npc_female");
            String actualVoiceProfile = allowedProfiles.contains(voiceProfile) ? voiceProfile : "narrator"; // Default to narrator if invalid

            // Create a mock URL incorporating the endpoint and voice profile
            // In a real implementation, you would make an HTTP request to ttsEndpoint here
            String audioId = String.valueOf(new Random().nextInt(100000));
            // Example: Use endpoint structure in the mock URL path
            String mockUrlPath = ttsEndpoint.replace("https://", "").replace("/", "_"); // e.g., api.deepseek.com_v3_tts
            String url = "mock://audio/" + mockUrlPath + "/" + actualVoiceProfile + "/" + audioId + ".mp3";

            result.put("text", text);
            result.put("voice_profile", actualVoiceProfile);
            result.put("audio_url", url); // Key name matches function description implicitly

            return result;
        } catch (JSONException e) {
            e.printStackTrace();
            return new JSONObject();
        }
    }

    // Corresponds to the main game loop logic described
    private JSONObject gameLoop(JSONObject worldState, String playerInput) {
        try {
            JSONObject finalResult = new JSONObject(); // Renamed for clarity

            // 1. Process Player Input (using existing process_player_input)
            JSONObject processedInput = process_player_input(playerInput);

            // Handle invalid input
            if (!processedInput.getBoolean("valid")) {
                finalResult.put("world_state", worldState); // Return original state

                JSONObject output = new JSONObject();
                output.put("narrative_text", processedInput.getString("error"));
                output.put("npc_dialogues", new JSONArray());
                output.put("audio_urls", new JSONArray()); // Use audio_urls for consistency

                finalResult.put("output", output);
                return finalResult;
            }

            // 2. Generate Narrative (using updated generate_narrative)
            JSONObject narrative = generate_narrative(worldState, processedInput);
            JSONArray npcDialogues = narrative.getJSONArray("npc_dialogues"); // Contains voice_profile now

            // 3. Update World State (using existing update_world_state)
            JSONObject updatedWorldState = update_world_state(worldState, narrative.getJSONArray("narrative_events"));

            // 4. Synthesize Voice for NPC dialogues
            JSONArray audioUrls = new JSONArray(); // Changed name from voice_urls
            final String DEEPSEEK_TTS_ENDPOINT = "https://api.deepseek.com/v3/tts"; // Defined endpoint

            for (int i = 0; i < npcDialogues.length(); i++) {
                JSONObject dialogue = npcDialogues.getJSONObject(i);
                // Call updated synthesize_voice with the required endpoint
                JSONObject voiceResult = synthesize_voice(
                    dialogue.getString("text"),
                    dialogue.getString("voice_profile"),
                    DEEPSEEK_TTS_ENDPOINT
                );

                // Store the resulting URL (or relevant info)
                JSONObject audioInfo = new JSONObject();
                audioInfo.put("speaker", dialogue.getString("speaker"));
                audioInfo.put("text", dialogue.getString("text"));
                audioInfo.put("audio_url", voiceResult.getString("audio_url")); // Get the URL from synthesize_voice result

                audioUrls.put(audioInfo);
            }

            // 5. Construct Final Output JSON Payload
            finalResult.put("world_state", updatedWorldState); // Updated state

            JSONObject output = new JSONObject();
            output.put("narrative_text", narrative.getString("narrative_text"));
            output.put("npc_dialogues", npcDialogues); // Include dialogues for context if needed
            output.put("audio_urls", audioUrls); // Array of synthesized audio info/URLs

            finalResult.put("output", output);

            return finalResult;
        } catch (JSONException e) {
            e.printStackTrace();
            try {
                JSONObject errorResult = new JSONObject();
                errorResult.put("world_state", worldState);

                JSONObject output = new JSONObject();
                output.put("narrative_text", "An error occurred while processing your action.");
                output.put("npc_dialogues", new JSONArray());
                output.put("audio_urls", new JSONArray());

                errorResult.put("output", output);
                return errorResult;
            } catch (JSONException ex) {
                // If even creating the error response fails, return an empty object
                return new JSONObject();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Release MediaPlayer resources
        for (MediaPlayer player : audioCache.values()) {
            if (player != null) {
                player.release();
            }
        }
        audioCache.clear();
    }
}