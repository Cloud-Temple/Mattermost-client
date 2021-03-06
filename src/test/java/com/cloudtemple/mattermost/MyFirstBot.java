package com.cloudtemple.mattermost;

import com.cloudtemple.mattermost.traders.Event;
import com.cloudtemple.mattermost.traders.channel.ChannelId;
import com.cloudtemple.mattermost.traders.post.Post;
import com.cloudtemple.mattermost.traders.user.User;
import com.cloudtemple.mattermost.traders.user.UserId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public class MyFirstBot
{

	public static final String personalAccessToken = "5wfggktfdirpiphejxjcxjboco"; // You need to activate the 'Personal Access Token' and create one.
	public static final String host = "localhost:8065";
	public static final String teamId = "jeciqwjze3b8jm3b3d4khneehy";

	public static final Map<String, String> FAQ = new HashMap<>();

	static
	{
		FAQ.put("where is brian", "In the kitchen!");
		FAQ.put("where is bryan", "In the kitchen!");
		FAQ.put("t'es qui", "Je suis un bot créé pour vous aider dans MH");
		FAQ.put("c'est quoi mh", "MH pour MountyHall. Si vous connaissez pas c'est que vous n'êtes pas au bon endroit");
		FAQ.put("comment tu t'appelle", "MountyBot. (Ouais c'est pas terrible, vous pouvez en toucher 2 mots à mon créateur)");
		FAQ.put("comment tu t'appelles", "MountyBot. (Ouais c'est pas terrible, vous pouvez en toucher 2 mots à mon créateur)");
		FAQ.put("tu sais faire quoi", "Plein de choses. Mais je ne sais pas trop quoi exactement pour l'instant :D"); // TODO: 15/09/18
		FAQ.put("tu parle le nabby", "J'essaye mais à chaque fois je segfault");
	}

	public static final Predicate<Event> IS_POSTED = event -> "posted".equals(event.event);
	public static final Predicate<Event> HAS_MENTIONS = event -> event.getData().containsKey("mentions");
	public static final Predicate<Event> ON_EXPECTED_TEAM = event ->
	{
		final Object currentTeamId = event.getData().get("team_id");
		final boolean result = teamId.equals(currentTeamId);
		if (!result)
			System.out.println("Unexpected teamId: " + currentTeamId);
		return result;
	};

	public static final String escapeForFaq(final String input)
	{
		// TODO: 15/09/18
		return input.trim().toLowerCase();
	}

	public static final String messageForMe(final User user, final Event message)
	{
		final String str = message.getPost().get().getMessage();
		final String mentionByName = "@" + user.username;
		final String messageWithoutMention = str.substring(mentionByName.length());
		final Optional<String> optional = asQuestion(messageWithoutMention);
		if (optional.isPresent())
		{
			final String question = optional.get();
			final String answer = FAQ.get(escapeForFaq(question));
			if (answer != null)
				return answer;

			final Optional<String> universalOptional = asWhatIsTheUniversalAnswer(question);
			if (universalOptional.isPresent())
				return "42";

			final Optional<String> wdyfOptional = asWhatDoYouThink(question);
			if (wdyfOptional.isPresent())
			{
				final String pipo = Pipotron.random();
				return String.format("%s ... %s", wdyfOptional.get(), pipo);
			}

			return "Y dit qu'il a pas compris la question ...";
		}
		return "Oh toi, ta gueule !";
	}

	private static Optional<String> asWhatDoYouThink(final String question)
	{
		final Set<String> sets = new HashSet<>();
		sets.add("que penses-tu de");
		sets.add("que penses tu de");
		sets.add("quel est ton avis sur");
		sets.add("quel est ton avis à propos de");

		for (final String set : sets)
			if (question.startsWith(set))
			{
				String wdfy = question.substring(set.length()).trim();
				wdfy = wdfy.substring(0, 1).toUpperCase() + wdfy.substring(1);
				return Optional.of(wdfy);
			}
		return Optional.empty();
	}

	private static Optional<String> asWhatIsTheUniversalAnswer(final String question)
	{
		final Set<String> sets = new HashSet<>();
		sets.add("quelle est la réponse");

		for (final String set : sets)
			if (question.startsWith(set))
			{
				String wdfy = question.substring(set.length()).trim();
				wdfy = wdfy.substring(0, 1).toUpperCase() + wdfy.substring(1);
				return Optional.of(wdfy);
			}
		return Optional.empty();
	}

	private static Optional<String> asQuestion(final String messageWithoutMention)
	{
		final String trimed = messageWithoutMention.trim();
		if (trimed.endsWith("?"))
		{
			String result = trimed;
			while (result.endsWith("?"))
			{
				result = result.substring(0, result.length() - 1);
				result = result.trim();
			}
			if (result.isEmpty())
				return Optional.empty();
			return Optional.of(result);
		}
		return Optional.empty();
	}

	public static void main(final String[] args)
	{
		final MatterMostBotClient syncClient = new MatterMostBotClient(host, personalAccessToken);
		final User user = syncClient.getUsersMe();
		final UserId userId = user.userId();

		final Predicate<Event> isMentionForMe = event ->
		{
			final String mentions = (String) event.getData().get("mentions");
			final boolean result = mentions.contains(userId.getId());
			return result;
		};
		final Predicate<Event> isExplicitMentionForMe = event ->
		{
			final String message = event.getPost().get().getMessage().trim();
			final String mentionByName = "@" + user.username;
			final boolean result = message.startsWith(mentionByName);
			return result;
		};

		final Predicate<Event> compliant = IS_POSTED.and(HAS_MENTIONS).and(isMentionForMe).and(isExplicitMentionForMe).and(ON_EXPECTED_TEAM);

		final MatterMostBotClient watchClient = new MatterMostBotClient(host, personalAccessToken, event ->
		{
			if (compliant.test(event))
			{
				final Post post = event.getPost().get();
				System.out.println("ALERTE ! On me pause une question !!!! " + post.getMessage());
				final ChannelId channelId = new ChannelId(post.getChannel_id());
				final String response = messageForMe(user, event);
				syncClient.sendPost(channelId, response);
				//                syncClient.sendPost(new PostRequest(channelId, "Oh toi ta gueule !", post.getId(), null));
			}
			else
				if (IS_POSTED.test(event))
				{
					final Post post = event.getPost().get();
					System.out.println("Message mais pas pour moi: " + post.getMessage());
				}
		});
		watchClient.connect();
		watchClient.await(600);
		watchClient.disconnect();

	}
}
