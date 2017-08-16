package buttondevteam.townyplotresizer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.FileUtil;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.reader.StreamReader;

import com.google.common.collect.Lists;

public class PlotResizer {
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws IOException {
		System.out.println("Towny plot resizer - https://github.com/TBMCPlugins/TownyPlotResizer");
		System.out.println("Make sure you have a backup of Towny data");
		System.out.println(
				"Changing the plot size can remove plot-specific data, or claim area that hasn't been claimed");
		System.out.println(
				"Only guaranteed to work well if the old plot size divided by the new is an integer or 1/integer");
		System.out.println();
		System.out.println("----");
		System.out.println();
		if (args.length == 0 || !isInteger(args[0], 10)) {
			System.out.println("Usage: java -jar PlotResizer.jar <newsize>");
			return;
		}
		int newsize = Integer.parseInt(args[0]);
		File file = new File("plugins/Towny/settings/config.yml");
		if (!file.exists()) {
			System.out.println("Cannot find file: " + file.getAbsolutePath());
			System.out.println("Make sure that this jar is in the server directory");
			return;
		}
		YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
		ConfigurationSection town = config.getConfigurationSection("town");
		int oldsize = Integer.parseInt(town.getString("town_block_size")); // getInt doesn't seem to work
		/*
		 * System.out.println(config.getConfigurationSection("town").getValues(false).keySet().stream() .map(Object::toString).collect(Collectors.joining(", ")));
		 */
		if (newsize == oldsize) {
			System.out.println("The block size is already " + newsize);
			return;
		}
		System.out.println("Proceed to change size to " + newsize + " from " + oldsize + "? (Y/n)");
		if (Character.toUpperCase(System.in.read()) != 'Y') {
			System.out.println("Stopping");
			return;
		}
		float ratio = oldsize / newsize;
		float ratio2D = ratio == 1 ? ratio : ratio > 1 ? ratio * 2 : ratio / 2;
		float priceratio = 1 / ratio2D;
		List<String> conflines = Files.readAllLines(file.toPath());
		conflines.forEach(
				s -> s = s.replace("town_block_size: '" + oldsize + "'", "town_block_size: '" + newsize + "'"));
		// SetValueFunction func = PlotResizer::setValue; - Works with generics
		SetValueFunction func = (path, parser, isfloat, isprice) -> {
			double oldval = parser == null ? isfloat ? config.getDouble(path) : config.getInt(path)
					: (float) parser.apply(config.getString(path));
			String[] spl = path.split("\\.");
			String pathend = spl[spl.length - 1];
			conflines.forEach(s -> s = s.replace(pathend + ": " + (parser != null ? "'" + oldval + "'" : oldval),
					pathend + ": " + (parser != null ? "'" : "")
							+ String.format(isfloat ? "%f" : "%d", oldval * (isprice ? priceratio : ratio2D))
							+ (parser != null ? "'" : "")));
		};
		System.out.println("Set block size to " + newsize);
		func.setValue("town.town_block_ratio", Integer::parseInt, false, false);
		System.out.println("Set block ratio to " + town.getString("town_block_ratio"));
		config.set("levels.town_level", Arrays.asList(config.getMapList("levels.town_level").stream().map(map -> {
			((Map<String, Object>) map).put("townBlockLimit", ((int) map.get("townBlockLimit")) * ratio2D);
			return map; // TODO: Store values in array, replace in same order
		}).toArray(Map[]::new)));
		System.out.println("Set town levels");
		town.set("max_plots_per_resident", Integer.parseInt(town.getString("max_plots_per_resident")) * ratio2D); // TODO
		System.out.println("Set max plots per resident to " + town.getString("max_plots_per_resident"));
		town.set("min_plot_distance_from_town_plot",
				Integer.parseInt(town.getString("min_plot_distance_from_town_plot")) * ratio2D);
		System.out.println("Set min plot distance to " + town.getString("min_plot_distance_from_town_plot"));
		town.set("min_distance_from_town_homeblock",
				Integer.parseInt(town.getString("min_distance_from_town_homeblock")) * ratio2D);
		System.out.println("Set min distance from homeblock to " + town.getString("min_distance_from_town_homeblock"));
		town.set("max_distance_between_homeblocks",
				Integer.parseInt(town.getString("max_distance_between_homeblocks")) * ratio2D);
		System.out.println("Set max distance from homeblock to " + town.getString("max_distance_between_homeblocks"));
		config.set("war.event.eco.wartime_town_block_loss_price",
				Float.parseFloat(config.getString("war.event.eco.wartime_town_block_loss_price")) * priceratio);
		System.out.println("Set wartime town block loss price to "
				+ config.getString("war.event.eco.wartime_town_block_loss_price"));
		ConfigurationSection new_expand = config.getConfigurationSection("economy.new_expand");
		new_expand.set("price_claim_townblock",
				Float.parseFloat(new_expand.getString("price_claim_townblock")) * priceratio);
		System.out.println("Set claim price to " + new_expand.getString("price_claim_townblock"));
		new_expand.set("price_purchased_bonus_townblock",
				Float.parseFloat(new_expand.getString("price_purchased_bonus_townblock")) * priceratio);
		System.out.println("Set bonus claim price to " + new_expand.getString("price_purchased_bonus_townblock"));
		new_expand.set("price_purchased_bonus_townblock_increase",
				Float.parseFloat(new_expand.getString("price_purchased_bonus_townblock_increase")) * priceratio);
		System.out.println("Set bonus claim price increase to "
				+ new_expand.getString("price_purchased_bonus_townblock_increase"));
		config.set("war.economy.townblock_won",
				Integer.parseInt(config.getString("war.economy.townblock_won")) * priceratio);
		System.out.println("Set town block won price to " + config.getString("war.economy.townblock_won"));
		// TODO: Comments get ERASED - Read then replace
		System.out.println("Saved Towny configuration");
		System.out.println();
		System.out.println("Setting town plots...");
		file = new File(file.getParentFile().getParentFile(), "data/townblocks");
		for (File f : file.listFiles()) {
			if (!f.isDirectory())
				continue;
			int C = 0;
			final File[] listFiles = f.listFiles();
			for (File ff : Arrays.stream(listFiles).sorted(
					(f1, f2) -> ratio > 1 ? f2.getName().compareTo(f1.getName()) : f1.getName().compareTo(f2.getName()))
					.toArray(s -> listFiles)) { // Make sure to not overwrite any other town blck waiting to be moved
				String[] coords = ff.getName().split("_");
				int x = Integer.parseInt(coords[0]), y = Integer.parseInt(coords[1]),
						size = Integer.parseInt(coords[2].split("\\.")[0]);
				if (size == oldsize) {
					for (int i = 0; i < ratio; i++)
						for (int j = 0; j < ratio; j++)
							Files.copy(
									ff.toPath(), ff.toPath().resolveSibling((x * (int) ratio + i) + "_"
											+ (y * (int) ratio + j) + "_" + (int) newsize + ".data"),
									StandardCopyOption.REPLACE_EXISTING);
					ff.delete();
					C++;
				}
			}
			System.out.println("Finished with " + f.getName() + ", renaming " + C + " blocks");
		} // TODO: Homeblock
	}

	public static boolean isInteger(String s, int radix) { // https://stackoverflow.com/a/5439547/2703239
		if (s.isEmpty())
			return false;
		for (int i = 0; i < s.length(); i++) {
			if (i == 0 && s.charAt(i) == '-') {
				if (s.length() == 1)
					return false;
				else
					continue;
			}
			if (Character.digit(s.charAt(i), radix) < 0)
				return false;
		}
		return true;
	}
}
