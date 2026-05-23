package EndZone.forms;

import EndZone.config.BotConfig;
import EndZone.services.ServiceManager;
import EndZone.database.DatabaseService;
import EndZone.util.ErrorService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;

import java.util.*;

public class EndZoneForm {
    private static final Map<String, FormState> formStates = new HashMap<>();
    private static final Set<String> modifiedUserIds = new HashSet<>();

    public static class FormState {
        public int currentQuestion = 1;
        public Map<Integer, String> answers = new HashMap<>();
        public User user;
        public String selectedRole = null;

        public FormState(User user) {
            this.user = user;
        }
    }

    public static FormState getOrCreateFormState(User user) {
        return formStates.computeIfAbsent(user.getId(), k -> {
            FormState dbState = DatabaseService.loadFormState(user.getId(), user);
            return dbState != null ? dbState : new FormState(user);
        });
    }

    public static void exportAllFormStatesToDatabase() {
        int count = 0;
        for (String userId : modifiedUserIds) {
            FormState state = formStates.get(userId);
            if (state != null) {
                DatabaseService.saveFormState(userId, state);
                count++;
            }
        }
        modifiedUserIds.clear();
        if (count > 0) {
            System.out.println("[DATABASE] Exported " + count + " modified form states to database");
        }
    }

    public static void startApplication(User user, ButtonInteractionEvent event) {
        FormState existingState = DatabaseService.loadFormState(user.getId(), user);
        
        if (existingState != null && existingState.currentQuestion > 1 && !existingState.answers.isEmpty()) {
            formStates.put(user.getId(), existingState);
            event.reply("You have an in-progress process! Check your DMs to continue.").setEphemeral(true).queue();
            System.out.println("[INFO] User " + user.getId() + " resuming process from Q" + existingState.currentQuestion);
            
            int currentQ = existingState.currentQuestion;
            boolean isModalQuestion = currentQ == 1 || currentQ == 6 || currentQ == 9 || currentQ == 10 || currentQ == 11 || currentQ == 13 || currentQ == 14 || currentQ == 15;
            
            user.openPrivateChannel().queue(
                privateChannel -> {
                    if (!isModalQuestion) {
                        EmbedBuilder welcomeBack = new EmbedBuilder()
                                .setDescription("Welcome back! You're on question **" + currentQ + " of 15**. Let's finish this up!")
                                .setColor(0x1f47cf);
                        privateChannel.sendMessageEmbeds(welcomeBack.build()).queue();
                    }
                    presentQuestionDM(existingState, user);
                },
                error -> ErrorService.sendErrorNotification("Failed to open DM channel for user " + user.getAsTag() + ": " + error.getMessage())
            );
        } else {
            FormState state = new FormState(user);
            state.currentQuestion = 1;
            state.answers.clear();
            formStates.put(user.getId(), state);
            DatabaseService.resetApplication(user.getId());
            
            event.reply("Check your DMs!").setEphemeral(true).queue();
            
            user.openPrivateChannel().queue(
                privateChannel -> {
                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle("EndZone Staff Application")
                            .setDescription("Hey! This is EndZone. Are you ready to proceed?")
                            .setColor(0x1f47cf);
                    
                    privateChannel.sendMessageEmbeds(embed.build())
                            .setActionRow(Button.primary("dm_ready_button", "Ready to Start"))
                            .queue(
                                null,
                                error -> ErrorService.sendErrorNotification("Failed to send application welcome message to " + user.getAsTag() + ": " + error.getMessage())
                            );
                },
                error -> ErrorService.sendErrorNotification("Failed to open DM channel for user " + user.getAsTag() + ": " + error.getMessage())
            );
        }
    }

    public static void handleDMReady(User user) {
        FormState state = getOrCreateFormState(user);
        
        user.openPrivateChannel().queue(
            privateChannel -> {
                EmbedBuilder embed = new EmbedBuilder()
                        .setDescription("Great! Let's get started. Please answer the following questions.")
                        .setColor(0x1f47cf);
                
                privateChannel.sendMessageEmbeds(embed.build()).queue();
            },
            error -> System.err.println("[ERROR] Failed to open user DM channel: " + error.getMessage())
        );
        
        presentQuestionDM(state, user);
    }

    public static void handleModalButton(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        int questionNum = extractQuestionNumberFromButton(componentId);
        
        Modal modal = null;
        switch (questionNum) {
            case 1:
                modal = Modal.create("q1_modal", "Question 1")
                        .addActionRow(TextInput.create("q1_answer", "What is your Discord Username & ID#?", TextInputStyle.SHORT)
                                .setPlaceholder("Example: Brulph, ID#123456789098765432 (both required)")
                                .setRequired(true)
                                .setMinLength(5)
                                .build())
                        .build();
                break;
            case 6:
                modal = Modal.create("q6_modal", "Question 6")
                        .addActionRow(TextInput.create("q6_answer", "Host/Streamer Apps", TextInputStyle.SHORT)
                                .setPlaceholder("Host/Streamer Applicants, Twitch link here.")
                                .setRequired(true)
                                .build())
                        .build();
                break;
            case 9:
                modal = Modal.create("q9_modal", "Question 9")
                        .addActionRow(TextInput.create("q9_answer", "Tell us a bit about yourself", TextInputStyle.PARAGRAPH)
                                .setPlaceholder("Share about yourself...")
                                .setRequired(true)
                                .setMinLength(10)
                                .build())
                        .build();
                break;
            case 10:
                modal = Modal.create("q10_modal", "Question 10")
                        .addActionRow(TextInput.create("q10_answer", "Why should we offer you staff?", TextInputStyle.PARAGRAPH)
                                .setPlaceholder("Why should we offer you the staff position?")
                                .setRequired(true)
                                .setMinLength(10)
                                .build())
                        .build();
                break;
            case 11:
                modal = Modal.create("q11_modal", "Question 11")
                        .addActionRow(TextInput.create("q11_answer", "What would you add to Endzone?", TextInputStyle.PARAGRAPH)
                                .setPlaceholder("What would you add and why?")
                                .setRequired(true)
                                .setMinLength(10)
                                .build())
                        .build();
                break;
            case 13:
                modal = Modal.create("q13_modal", "Question 13")
                        .addActionRow(TextInput.create("q13_answer", "Please let us know your experience.", TextInputStyle.PARAGRAPH)
                                .setPlaceholder("Share your previous experience...")
                                .setRequired(true)
                                .setMinLength(10)
                                .build())
                        .build();
                break;
            case 14:
                modal = Modal.create("q14_modal", "Question 14")
                        .addActionRow(TextInput.create("q14_answer", "How would you help the event if given staff?", TextInputStyle.PARAGRAPH)
                                .setPlaceholder("What and how would you contribute?")
                                .setRequired(true)
                                .setMinLength(10)
                                .build())
                        .build();
                break;
            case 15:
                modal = Modal.create("q15_modal", "Question 15")
                        .addActionRow(TextInput.create("q15_answer", "You've been given an elephant...", TextInputStyle.PARAGRAPH)
                                .setPlaceholder("What would you do with the elephant?")
                                .setRequired(true)
                                .setMinLength(5)
                                .build())
                        .build();
                break;
        }
        
        if (modal != null) {
            event.replyModal(modal).queue(
                null,
                error -> System.err.println("[ERROR] Failed to display modal: " + error.getMessage())
            );
        }
    }

    private static int extractQuestionNumberFromButton(String componentId) {
        String numStr = componentId.replace("q", "").replace("_answer_button", "");
        return Integer.parseInt(numStr);
    }

    public static void handleNextButton(ButtonInteractionEvent event) {
        FormState state = getOrCreateFormState(event.getUser());
        state.currentQuestion++;
        modifiedUserIds.add(event.getUser().getId());
        DatabaseService.saveFormState(event.getUser().getId(), state);
        presentQuestionDM(state, event.getUser());
    }

    public static void handleQuestionAnswer(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        FormState state = getOrCreateFormState(event.getUser());

        if (componentId.startsWith("q") && componentId.contains("_yes")) {
            int questionNum = extractQuestionNumber(componentId);
            state.answers.put(questionNum, "Yes");
        } else if (componentId.startsWith("q") && componentId.contains("_no")) {
            int questionNum = extractQuestionNumber(componentId);
            state.answers.put(questionNum, "No");
        }

        state.currentQuestion++;
        modifiedUserIds.add(event.getUser().getId());
        DatabaseService.saveFormState(event.getUser().getId(), state);
        event.deferEdit().queue();
        presentQuestionDM(state, event.getUser());
    }

    public static void handleSelectAnswer(StringSelectInteractionEvent event) {
        String componentId = event.getComponentId();
        FormState state = getOrCreateFormState(event.getUser());

        if (componentId.startsWith("q")) {
            int questionNum = extractQuestionNumber(componentId);
            String selected = event.getValues().get(0);
            state.answers.put(questionNum, selected);
            
            if (questionNum == 2) {
                state.selectedRole = selected;
            }
        }

        state.currentQuestion++;
        modifiedUserIds.add(event.getUser().getId());
        DatabaseService.saveFormState(event.getUser().getId(), state);
        event.deferEdit().queue();
        presentQuestionDM(state, event.getUser());
    }

    public static void handleModalResponse(ModalInteractionEvent event) {
        FormState state = getOrCreateFormState(event.getUser());
        String modalId = event.getModalId();

        if (modalId.startsWith("q")) {
            int questionNum = Integer.parseInt(modalId.replace("q", "").replace("_modal", ""));
            boolean answerStored = false;
            for (ModalMapping mapping : event.getValues()) {
                String answer = mapping.getAsString();
                if (answer != null && !answer.isEmpty()) {
                    state.answers.put(questionNum, answer);
                    answerStored = true;
                    break;
                }
            }
            if (!answerStored) {
                System.err.println("[WARNING] No answer found for question " + questionNum + " from " + event.getUser().getAsTag());
                state.answers.put(questionNum, "[No response provided]");
            }
        }

        state.currentQuestion++;
        modifiedUserIds.add(event.getUser().getId());
        DatabaseService.saveFormState(event.getUser().getId(), state);
        event.deferReply().setEphemeral(true).queue();
        presentQuestionDM(state, event.getUser());
    }

    private static int extractQuestionNumber(String componentId) {
        String[] parts = componentId.split("_");
        return Integer.parseInt(parts[0].substring(1));
    }

    private static void presentQuestion(FormState state, Object event) {
        int questionNum = state.currentQuestion;

        if (shouldSkipQuestion(state, questionNum)) {
            state.currentQuestion++;
            presentQuestion(state, event);
            return;
        }

        if (questionNum > 15) {
            submitApplication(state, event);
            return;
        }

        switch (questionNum) {
            case 1:
                presentDiscordUsernameQuestion(state, event);
                break;
            case 2:
                presentRoleSelectionQuestion(state, event);
                break;
            case 3:
                presentWeekdayModerationQuestion(state, event);
                break;
            case 4:
                presentSundayEventQuestion(state, event);
                break;
            case 5:
                presentRegionSelectionQuestion(state, event);
                break;
            case 6:
                presentTwitchLinkQuestion(state, event);
                break;
            case 7:
                presentFacecamQuestion(state, event);
                break;
            case 8:
                presentMicQuestion(state, event);
                break;
            case 9:
                presentAboutYourselfQuestion(state, event);
                break;
            case 10:
                presentWhyStaffQuestion(state, event);
                break;
            case 11:
                presentAddToEndzoneQuestion(state, event);
                break;
            case 12:
                presentPreviousExperienceQuestion(state, event);
                break;
            case 13:
                presentExperienceDetailsQuestion(state, event);
                break;
            case 14:
                presentContributionQuestion(state, event);
                break;
            case 15:
                presentElephantQuestion(state, event);
                break;
        }
    }

    private static void presentQuestionDM(FormState state, User user) {
        int questionNum = state.currentQuestion;

        if (shouldSkipQuestion(state, questionNum)) {
            state.currentQuestion++;
            presentQuestionDM(state, user);
            return;
        }

        if (questionNum > 15) {
            submitApplicationDM(state, user);
            return;
        }

        user.openPrivateChannel().queue(
            privateChannel -> presentQuestionToDM(questionNum, state, privateChannel),
            error -> ErrorService.sendErrorNotification("Failed to open DM channel for question " + questionNum + " for user " + user.getAsTag() + ": " + error.getMessage())
        );
    }

    private static void presentQuestionToDM(int questionNum, FormState state, net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel privateChannel) {
            switch (questionNum) {
                case 1:
                    presentDiscordUsernameQuestionDM(state, privateChannel);
                    break;
                case 2:
                    presentRoleSelectionQuestionDM(state, privateChannel);
                    break;
                case 3:
                    presentWeekdayModerationQuestionDM(state, privateChannel);
                    break;
                case 4:
                    presentSundayEventQuestionDM(state, privateChannel);
                    break;
                case 5:
                    presentRegionSelectionQuestionDM(state, privateChannel);
                    break;
                case 6:
                    presentTwitchLinkQuestionDM(state, privateChannel);
                    break;
                case 7:
                    presentFacecamQuestionDM(state, privateChannel);
                    break;
                case 8:
                    presentMicQuestionDM(state, privateChannel);
                    break;
                case 9:
                    presentAboutYourselfQuestionDM(state, privateChannel);
                    break;
                case 10:
                    presentWhyStaffQuestionDM(state, privateChannel);
                    break;
                case 11:
                    presentAddToEndzoneQuestionDM(state, privateChannel);
                    break;
                case 12:
                    presentPreviousExperienceQuestionDM(state, privateChannel);
                    break;
                case 13:
                    presentExperienceDetailsQuestionDM(state, privateChannel);
                    break;
                case 14:
                    presentContributionQuestionDM(state, privateChannel);
                    break;
                case 15:
                    presentElephantQuestionDM(state, privateChannel);
                    break;
            }
    }

    private static boolean shouldSkipQuestion(FormState state, int questionNum) {
        String role = state.selectedRole;
        
        if (role != null) {
            if (role.equals("Trial Moderator")) {
                if (questionNum == 6 || questionNum == 7 || questionNum == 8) {
                    return true;
                }
            } else if (role.equals("Host/Streamer")) {
                if (questionNum == 3 || questionNum == 4 || questionNum == 5) {
                    return true;
                }
            }
        }
        
        String previousExp = state.answers.get(12);
        if (questionNum == 13 && !"Yes".equals(previousExp)) {
            return true;
        }

        return false;
    }

    private static void presentDiscordUsernameQuestion(FormState state, Object event) {
        Modal modal = Modal.create("q1_modal", "Question 1")
                .addActionRow(TextInput.create("q1_answer", "What is your Discord Username & ID#?", TextInputStyle.SHORT)
                        .setPlaceholder("Example: Brulph, ID#123456789098765432 (both required)")
                        .setRequired(true)
                        .setMinLength(5)
                        .build())
                .build();

        replyWithModal(event, modal);
    }

    private static void presentRoleSelectionQuestion(FormState state, Object event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setDescription("Which role are you interested in?")
                .setColor(0x1f47cf);

        StringSelectMenu menu = StringSelectMenu.create("q2_select")
                .addOption("Trial Moderator", "Trial Moderator")
                .addOption("Host/Streamer", "Host/Streamer")
                .setMaxValues(1)
                .setMinValues(1)
                .build();

        replyWithSelectMenu(event, embed.build(), menu);
    }

    private static void presentWeekdayModerationQuestion(FormState state, Object event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setDescription("Will you be able to **__actually__** moderate the EndZone server during the week/weekends?")
                .setColor(0x1f47cf);

        replyWithYesNo(event, embed.build(), 3);
    }

    private static void presentSundayEventQuestion(FormState state, Object event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setDescription("Will you be available to **__actually__** work EndZone Event on Sundays? (not just play)")
                .setColor(0x1f47cf);

        replyWithYesNo(event, embed.build(), 4);
    }

    private static void presentRegionSelectionQuestion(FormState state, Object event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setDescription("What is your region?")
                .setColor(0x1f47cf);

        StringSelectMenu menu = StringSelectMenu.create("q5_select")
                .addOption("USE", "USE")
                .addOption("USW", "USW")
                .addOption("EU", "EU")
                .addOption("ASIA", "ASIA")
                .setMaxValues(1)
                .setMinValues(1)
                .build();

        replyWithSelectMenu(event, embed.build(), menu);
    }

    private static void presentTwitchLinkQuestion(FormState state, Object event) {
        Modal modal = Modal.create("q6_modal", "Question 6")
                .addActionRow(TextInput.create("q6_answer", "Host/Streamer Apps", TextInputStyle.SHORT)
                        .setPlaceholder("Host/Streamer Applicants, Twitch link here.")
                        .setRequired(true)
                        .build())
                .build();

        replyWithModal(event, modal);
    }

    private static void presentFacecamQuestion(FormState state, Object event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setDescription("Do you have a facecam? (only required for those applying for Host/Streamer)")
                .setColor(0x1f47cf);

        replyWithYesNo(event, embed.build(), 7);
    }

    private static void presentMicQuestion(FormState state, Object event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setDescription("Do you have a mic? (only required for those applying for Host/Streamer)")
                .setColor(0x1f47cf);

        replyWithYesNo(event, embed.build(), 8);
    }

    private static void presentAboutYourselfQuestion(FormState state, Object event) {
        Modal modal = Modal.create("q9_modal", "Question 9")
                .addActionRow(TextInput.create("q9_answer", "Tell us a bit about yourself", TextInputStyle.PARAGRAPH)
                        .setPlaceholder("Share about yourself...")
                        .setRequired(true)
                        .setMinLength(10)
                        .build())
                .build();

        replyWithModal(event, modal);
    }

    private static void presentWhyStaffQuestion(FormState state, Object event) {
        Modal modal = Modal.create("q10_modal", "Question 10")
                .addActionRow(TextInput.create("q10_answer", "Why should we offer you staff?", TextInputStyle.PARAGRAPH)
                        .setPlaceholder("Why should we offer you the staff position?")
                        .setRequired(true)
                        .setMinLength(10)
                        .build())
                .build();

        replyWithModal(event, modal);
    }

    private static void presentAddToEndzoneQuestion(FormState state, Object event) {
        Modal modal = Modal.create("q11_modal", "Question 11")
                .addActionRow(TextInput.create("q11_answer", "What would you add to Endzone?", TextInputStyle.PARAGRAPH)
                        .setPlaceholder("What would you add and why?")
                        .setRequired(true)
                        .setMinLength(10)
                        .build())
                .build();

        replyWithModal(event, modal);
    }

    private static void presentPreviousExperienceQuestion(FormState state, Object event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setDescription("Do you have any previous experience?")
                .setColor(0x1f47cf);

        replyWithYesNo(event, embed.build(), 12);
    }

    private static void presentExperienceDetailsQuestion(FormState state, Object event) {
        Modal modal = Modal.create("q13_modal", "Question 13")
                .addActionRow(TextInput.create("q13_answer", "Please let us know your experience.", TextInputStyle.PARAGRAPH)
                        .setPlaceholder("Share your previous experience...")
                        .setRequired(true)
                        .setMinLength(10)
                        .build())
                .build();

        replyWithModal(event, modal);
    }

    private static void presentContributionQuestion(FormState state, Object event) {
        Modal modal = Modal.create("q14_modal", "Question 14")
                .addActionRow(TextInput.create("q14_answer", "How would you help the event if given staff?", TextInputStyle.PARAGRAPH)
                        .setPlaceholder("What and how would you contribute?")
                        .setRequired(true)
                        .setMinLength(10)
                        .build())
                .build();

        replyWithModal(event, modal);
    }

    private static void presentElephantQuestion(FormState state, Object event) {
        Modal modal = Modal.create("q15_modal", "Question 15")
                .addActionRow(TextInput.create("q15_answer", "You've been given an elephant...", TextInputStyle.PARAGRAPH)
                        .setPlaceholder("You've been given an elephant. You can't give it away or sell it. What would you do with the elephant?")
                        .setRequired(true)
                        .setMinLength(5)
                        .build())
                .build();

        replyWithModal(event, modal);
    }

    private static void presentDiscordUsernameQuestionDM(FormState state, net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel channel) {
        if (channel != null) {
            channel.sendMessage("**Question 1:** What is your Discord Username & ID#?")
                    .setActionRow(Button.primary("q1_answer_button", "Answer Question 1"))
                    .queue(
                        null,
                        error -> System.err.println("[ERROR] Failed to send Question 1 to " + channel.getUser().getAsTag() + ": " + error.getMessage())
                    );
        }
    }

    private static void presentRoleSelectionQuestionDM(FormState state, net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel channel) {
        EmbedBuilder embed = new EmbedBuilder()
                .setDescription("**Question 2:** Which role are you interested in?")
                .setColor(0x1f47cf);

        StringSelectMenu menu = StringSelectMenu.create("q2_select")
                .addOption("Trial Moderator", "Trial Moderator")
                .addOption("Host/Streamer", "Host/Streamer")
                .setMaxValues(1)
                .setMinValues(1)
                .build();

        channel.sendMessageEmbeds(embed.build())
                .setActionRow(menu)
                .queue(
                    null,
                    error -> ErrorService.sendErrorNotification("Failed to send Question 2 to " + channel.getUser().getAsTag() + ": " + error.getMessage())
                );
    }

    private static void presentWeekdayModerationQuestionDM(FormState state, net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel channel) {
        EmbedBuilder embed = new EmbedBuilder()
                .setDescription("**Question 3:** Will you be able to **__actually__** moderate the EndZone server during the week/weekends?")
                .setColor(0x1f47cf);

        Button yesBtn = Button.success("q3_yes", "Yes");
        Button noBtn = Button.danger("q3_no", "No");

        channel.sendMessageEmbeds(embed.build())
                .setActionRow(yesBtn, noBtn)
                .queue(
                    null,
                    error -> System.err.println("[ERROR] Failed to send Question 3 to " + channel.getUser().getAsTag() + ": " + error.getMessage())
                );
    }

    private static void presentSundayEventQuestionDM(FormState state, net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel channel) {
        EmbedBuilder embed = new EmbedBuilder()
                .setDescription("**Question 4:** Will you be available to **__actually__** work EndZone Event on Sundays? (not just play)")
                .setColor(0x1f47cf);

        Button yesBtn = Button.success("q4_yes", "Yes");
        Button noBtn = Button.danger("q4_no", "No");

        channel.sendMessageEmbeds(embed.build())
                .setActionRow(yesBtn, noBtn)
                .queue(
                    null,
                    error -> System.err.println("[ERROR] Failed to send Question 4 to " + channel.getUser().getAsTag() + ": " + error.getMessage())
                );
    }

    private static void presentRegionSelectionQuestionDM(FormState state, net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel channel) {
        EmbedBuilder embed = new EmbedBuilder()
                .setDescription("**Question 5:** What is your region?")
                .setColor(0x1f47cf);

        StringSelectMenu menu = StringSelectMenu.create("q5_select")
                .addOption("USE", "USE")
                .addOption("USW", "USW")
                .addOption("EU", "EU")
                .addOption("ASIA", "ASIA")
                .setMaxValues(1)
                .setMinValues(1)
                .build();

        channel.sendMessageEmbeds(embed.build())
                .setActionRow(menu)
                .queue(
                    null,
                    error -> System.err.println("[ERROR] Failed to send Question 5 to " + channel.getUser().getAsTag() + ": " + error.getMessage())
                );
    }

    private static void presentTwitchLinkQuestionDM(FormState state, net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel channel) {
        if (channel != null) {
            channel.sendMessage("**Question 6:** Host/Streamer Apps - Please provide your Twitch link.")
                    .setActionRow(Button.primary("q6_answer_button", "Answer Question 6"))
                    .queue(
                        null,
                        error -> ErrorService.sendErrorNotification("Failed to send Question 6 to " + channel.getUser().getAsTag() + ": " + error.getMessage())
                    );
        }
    }

    private static void presentFacecamQuestionDM(FormState state, net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel channel) {
        EmbedBuilder embed = new EmbedBuilder()
                .setDescription("**Question 7:** Do you have a facecam? (only required for those applying for Host/Streamer)")
                .setColor(0x1f47cf);

        Button yesBtn = Button.success("q7_yes", "Yes");
        Button noBtn = Button.danger("q7_no", "No");

        channel.sendMessageEmbeds(embed.build())
                .setActionRow(yesBtn, noBtn)
                .queue(
                    null,
                    error -> System.err.println("[ERROR] Failed to send Question 7 to " + channel.getUser().getAsTag() + ": " + error.getMessage())
                );
    }

    private static void presentMicQuestionDM(FormState state, net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel channel) {
        EmbedBuilder embed = new EmbedBuilder()
                .setDescription("**Question 8:** Do you have a mic? (only required for those applying for Host/Streamer)")
                .setColor(0x1f47cf);

        Button yesBtn = Button.success("q8_yes", "Yes");
        Button noBtn = Button.danger("q8_no", "No");

        channel.sendMessageEmbeds(embed.build())
                .setActionRow(yesBtn, noBtn)
                .queue(
                    null,
                    error -> System.err.println("[ERROR] Failed to send Question 8 to " + channel.getUser().getAsTag() + ": " + error.getMessage())
                );
    }

    private static void presentAboutYourselfQuestionDM(FormState state, net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel channel) {
        if (channel != null) {
            channel.sendMessage("**Question 9:** Tell us a bit about yourself.")
                    .setActionRow(Button.primary("q9_answer_button", "Answer Question 9"))
                    .queue(
                        null,
                        error -> System.err.println("[ERROR] Failed to send Question 9 to " + channel.getUser().getAsTag() + ": " + error.getMessage())
                    );
        }
    }

    private static void presentWhyStaffQuestionDM(FormState state, net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel channel) {
        if (channel != null) {
            channel.sendMessage("**Question 10:** Why should we offer you staff?")
                    .setActionRow(Button.primary("q10_answer_button", "Answer Question 10"))
                    .queue(
                        null,
                        error -> System.err.println("[ERROR] Failed to send Question 10 to " + channel.getUser().getAsTag() + ": " + error.getMessage())
                    );
        }
    }

    private static void presentAddToEndzoneQuestionDM(FormState state, net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel channel) {
        if (channel != null) {
            channel.sendMessage("**Question 11:** If you could add something in the Endzone, what would it be and why?")
                    .setActionRow(Button.primary("q11_answer_button", "Answer Question 11"))
                    .queue(
                        null,
                        error -> System.err.println("[ERROR] Failed to send Question 11 to " + channel.getUser().getAsTag() + ": " + error.getMessage())
                    );
        }
    }

    private static void presentPreviousExperienceQuestionDM(FormState state, net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel channel) {
        EmbedBuilder embed = new EmbedBuilder()
                .setDescription("**Question 12:** Do you have any previous experience?")
                .setColor(0x1f47cf);

        Button yesBtn = Button.success("q12_yes", "Yes");
        Button noBtn = Button.danger("q12_no", "No");

        channel.sendMessageEmbeds(embed.build())
                .setActionRow(yesBtn, noBtn)
                .queue(
                    null,
                    error -> System.err.println("[ERROR] Failed to send Question 12 to " + channel.getUser().getAsTag() + ": " + error.getMessage())
                );
    }

    private static void presentExperienceDetailsQuestionDM(FormState state, net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel channel) {
        if (channel != null) {
            channel.sendMessage("**Question 13:** If you answered Yes above, please let us know your experience.")
                    .setActionRow(Button.primary("q13_answer_button", "Answer Question 13"))
                    .queue(
                        null,
                        error -> ErrorService.sendErrorNotification("Failed to send Question 13 to " + channel.getUser().getAsTag() + ": " + error.getMessage())
                    );
        }
    }

    private static void presentContributionQuestionDM(FormState state, net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel channel) {
        if (channel != null) {
            channel.sendMessage("**Question 14:** If you are given staff, what and how would you contribute towards the event?")
                    .setActionRow(Button.primary("q14_answer_button", "Answer Question 14"))
                    .queue(
                        null,
                        error -> System.err.println("[ERROR] Failed to send Question 14 to " + channel.getUser().getAsTag() + ": " + error.getMessage())
                    );
        }
    }

    private static void presentElephantQuestionDM(FormState state, net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel channel) {
        if (channel != null) {
            channel.sendMessage("**Question 15:** You've been given an elephant. You can't give it away or sell it. What would you do with the elephant? (This is a Brulph Original, so it will always stay... just answer as best you can. ;o)")
                    .setActionRow(Button.primary("q15_answer_button", "Answer Question 15"))
                    .queue(
                        null,
                        error -> ErrorService.sendErrorNotification("Failed to send Question 15 to " + channel.getUser().getAsTag() + ": " + error.getMessage())
                    );
        }
    }

    private static void submitApplication(FormState state, Object event) {
        String[] questions = {
            "What is your Discord Username & ID#? (Example: Brulph, ID#123456789098765432) You will need to put both or your submission will not be considered.",
            "Which role are you interested in?",
            "Will you be able to actually moderate the EndZone server during the week/weekends?",
            "Will you be available to actually work EndZone Events on Sundays? (not just play)",
            "Region",
            "If you're applying for Host/Streamer, please list your Twitch link below.",
            "Do you have a facecam? (only required for those applying for Host/Streamer)",
            "Do you have a mic? (only required for those applying for Host/Streamer)",
            "Tell us a bit about yourself.",
            "Why should we offer you the staff position?",
            "If you could add something in the Endzone, what would it be and why?",
            "Do you have any previous experience?",
            "If you answered Yes above, please let us know your experience.",
            "If you are given staff, what and how would you contribute towards the event?",
            "You've been given an elephant. You can't give it away or sell it. What would you do with the elephant? (This is a Brulph Original, so it will always stay... just answer as best you can. ;o)"
        };

        int[][] groupRanges = {{1, 4}, {5, 9}, {10, 13}, {14, 14}, {15, 15}};
        
        for (int groupIndex = 0; groupIndex < groupRanges.length; groupIndex++) {
            int startQ = groupRanges[groupIndex][0];
            int endQ = groupRanges[groupIndex][1];
            
            StringBuilder responseText = new StringBuilder();
            if (groupIndex == 0) {
                responseText.append("**User:** ").append(state.user.getAsMention()).append(" (ID: `").append(state.user.getId()).append("`)\n\n");
            }
            
            for (int i = startQ; i <= endQ; i++) {
                if (state.answers.containsKey(i)) {
                    responseText.append("**Q").append(i).append(": ").append(questions[i - 1]).append("**\n");
                    responseText.append(state.answers.get(i)).append("\n\n");
                } else {
                    System.out.println("[DEBUG] Q" + i + " not found in state.answers for user " + state.user.getId() + " during submission part " + (groupIndex + 1));
                }
            }
            
            System.out.println("[DEBUG] Submitting part " + (groupIndex + 1) + " for user " + state.user.getId() + " - Total answers in state: " + state.answers.size());
            
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("New EndZone Submission - Part " + (groupIndex + 1) + " of 5")
                    .setDescription(responseText.toString())
                    .setColor(0x1f47cf);

            replyWithEmbed(event, embed.build());
        }
        
        DatabaseService.markApplicationSubmitted(state.user.getId());
        formStates.remove(state.user.getId());
    }

    private static void submitApplicationDM(FormState state, User user) {
        String[] questions = {
            "What is your Discord Username & ID#? (Example: Brulph, ID#123456789098765432) You will need to put both or your submission will not be considered.",
            "Which role are you interested in?",
            "Will you be able to actually moderate the EndZone server during the week/weekends?",
            "Will you be available to actually work EndZone Events on Sundays? (not just play)",
            "Region",
            "If you're applying for Host/Streamer, please list your Twitch link below.",
            "Do you have a facecam? (only required for those applying for Host/Streamer)",
            "Do you have a mic? (only required for those applying for Host/Streamer)",
            "Tell us a bit about yourself.",
            "Why should we offer you the staff position?",
            "If you could add something in the Endzone, what would it be and why?",
            "Do you have any previous experience?",
            "If you answered Yes above, please let us know your experience.",
            "If you are given staff, what and how would you contribute towards the event?",
            "You've been given an elephant. You can't give it away or sell it. What would you do with the elephant? (This is a Brulph Original, so it will always stay... just answer as best you can. ;o)"
        };

        user.openPrivateChannel().queue(
            privateChannel -> {
                EmbedBuilder confirmEmbed = new EmbedBuilder()
                        .setTitle("Submission Received!")
                        .setDescription("Thank you for your response! Your entry has been submitted successfully.\n\nCheck into <#790163120483205172> to see if you got promoted or not!")
                        .setColor(0x00ff00);
                privateChannel.sendMessageEmbeds(confirmEmbed.build()).queue(
                    null,
                    error -> ErrorService.sendErrorNotification("Failed to send application confirmation to " + user.getAsTag() + ": " + error.getMessage())
                );
            },
            error -> ErrorService.sendErrorNotification("Failed to open DM channel for user " + user.getAsTag() + " during submission: " + error.getMessage())
        );

        if (ServiceManager.getJda() != null) {
            net.dv8tion.jda.api.entities.channel.concrete.TextChannel staffAppsChannel = 
                ServiceManager.getJda().getTextChannelById(BotConfig.APPLICATION_CHANNEL_ID);
            if (staffAppsChannel != null) {
                int[][] groupRanges = {{1, 4}, {5, 9}, {10, 13}, {14, 14}, {15, 15}};
                
                for (int groupIndex = 0; groupIndex < groupRanges.length; groupIndex++) {
                    final int finalGroupIndex = groupIndex;
                    int startQ = groupRanges[finalGroupIndex][0];
                    int endQ = groupRanges[finalGroupIndex][1];
                    
                    StringBuilder responseText = new StringBuilder();
                    if (finalGroupIndex == 0) {
                        responseText.append("**User:** ").append(user.getAsMention()).append(" (ID: `").append(user.getId()).append("`)\n\n");
                    }
                    
                    for (int i = startQ; i <= endQ; i++) {
                        if (state.answers.containsKey(i)) {
                            responseText.append("**Q").append(i).append(": ").append(questions[i - 1]).append("**\n");
                            responseText.append(state.answers.get(i)).append("\n\n");
                        } else {
                            System.out.println("[DEBUG] Q" + i + " not found in state.answers for user " + user.getId() + " during submission part " + (finalGroupIndex + 1));
                        }
                    }
                    
                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle("New EndZone Submission - Part " + (finalGroupIndex + 1) + " of 5")
                            .setDescription(responseText.toString())
                            .setColor(0x1f47cf);
                    
                    if (finalGroupIndex == groupRanges.length - 1) {
                        Button approveBtn = Button.success("approve_" + user.getId(), "Approve");
                        Button denyBtn = Button.danger("deny_" + user.getId(), "Deny");
                        
                        staffAppsChannel.sendMessageEmbeds(embed.build())
                                .setActionRow(approveBtn, denyBtn)
                                .queue(
                                    null,
                                    error -> ErrorService.sendErrorNotification("Failed to post submission part " + (finalGroupIndex + 1) + " to review channel for " + user.getAsTag() + ": " + error.getMessage())
                                );
                    } else {
                        staffAppsChannel.sendMessageEmbeds(embed.build())
                                .queue(
                                    null,
                                    error -> ErrorService.sendErrorNotification("Failed to post submission part " + (finalGroupIndex + 1) + " to review channel for " + user.getAsTag() + ": " + error.getMessage())
                                );
                    }
                }
            } else {
                ErrorService.sendErrorNotification("EndZone channel not found!");
            }
        } else {
            ErrorService.sendErrorNotification("JDA instance is null - unable to post submission!");
        }

        DatabaseService.markApplicationSubmitted(user.getId());
        formStates.remove(user.getId());
    }

    private static void replyWithModal(Object event, Modal modal) {
        if (event instanceof ButtonInteractionEvent) {
            ((ButtonInteractionEvent) event).replyModal(modal).queue();
        } else if (event instanceof StringSelectInteractionEvent) {
            ((StringSelectInteractionEvent) event).replyModal(modal).queue();
        }
    }

    private static void replyWithSelectMenu(Object event, net.dv8tion.jda.api.entities.MessageEmbed embed, StringSelectMenu menu) {
        if (event instanceof ButtonInteractionEvent) {
            ((ButtonInteractionEvent) event).replyEmbeds(embed)
                    .setActionRow(menu)
                    .setEphemeral(true)
                    .queue();
        } else if (event instanceof ModalInteractionEvent) {
            ((ModalInteractionEvent) event).replyEmbeds(embed)
                    .setActionRow(menu)
                    .setEphemeral(true)
                    .queue();
        } else if (event instanceof StringSelectInteractionEvent) {
            ((StringSelectInteractionEvent) event).replyEmbeds(embed)
                    .setActionRow(menu)
                    .setEphemeral(true)
                    .queue();
        }
    }

    private static void replyWithYesNo(Object event, net.dv8tion.jda.api.entities.MessageEmbed embed, int questionNum) {
        Button yesBtn = Button.success("q" + questionNum + "_yes", "Yes");
        Button noBtn = Button.danger("q" + questionNum + "_no", "No");

        if (event instanceof ButtonInteractionEvent) {
            ((ButtonInteractionEvent) event).replyEmbeds(embed)
                    .setActionRow(yesBtn, noBtn)
                    .setEphemeral(true)
                    .queue();
        } else if (event instanceof ModalInteractionEvent) {
            ((ModalInteractionEvent) event).replyEmbeds(embed)
                    .setActionRow(yesBtn, noBtn)
                    .setEphemeral(true)
                    .queue();
        } else if (event instanceof StringSelectInteractionEvent) {
            ((StringSelectInteractionEvent) event).replyEmbeds(embed)
                    .setActionRow(yesBtn, noBtn)
                    .setEphemeral(true)
                    .queue();
        }
    }

    private static void replyWithEmbed(Object event, net.dv8tion.jda.api.entities.MessageEmbed embed) {
        if (event instanceof ButtonInteractionEvent) {
            ((ButtonInteractionEvent) event).replyEmbeds(embed)
                    .setEphemeral(false)
                    .queue();
        } else if (event instanceof ModalInteractionEvent) {
            ((ModalInteractionEvent) event).replyEmbeds(embed)
                    .setEphemeral(false)
                    .queue();
        } else if (event instanceof StringSelectInteractionEvent) {
            ((StringSelectInteractionEvent) event).replyEmbeds(embed)
                    .setEphemeral(false)
                    .queue();
        }
    }
}
