package buttondevteam.townyplotresizer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public class PlotResizer {
	private static Stream<String> conflines;

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
		conflines = Files.readAllLines(file.toPath()).stream();
		conflines = conflines
				.map(s -> s.replace("town_block_size: '" + oldsize + "'", "town_block_size: '" + newsize + "'"));
		System.out.println("Set block size to " + newsize);
		// SetValueFunction func = PlotResizer::setValue; - Works with generics
		SetValueFunction func = (path, parser, isfloat, isprice) -> {
			double oldval = parser == null ? isfloat ? config.getDouble(path) : config.getInt(path)
					: parser.apply(config.getString(path)).floatValue();
			String[] spl = path.split("\\.");
			String pathend = spl[spl.length - 1];
			System.out.println("Replace \"" + pathend + ": " + (parser != null ? "'" : "")
					+ String.format(Locale.ROOT, isfloat ? "%s" : "%.0f", oldval) + (parser != null ? "'" : "") + "\"");
			System.out.println("With \"" + pathend + ": " + (parser != null ? "'" : "")
					+ String.format(Locale.ROOT, isfloat ? "%s" : "%.0f", oldval * (isprice ? priceratio : ratio2D))
					+ (parser != null ? "'" : "") + "\"");
			conflines = conflines.map(s -> s.replace(
					pathend + ": " + (parser != null ? "'" : "")
							+ String.format(Locale.ROOT, isfloat ? "%s" : "%.0f", oldval) + (parser != null ? "'" : ""),
					pathend + ": " + (parser != null ? "'" : "") + String.format(Locale.ROOT, isfloat ? "%s" : "%.0f",
							oldval * (isprice ? priceratio : ratio2D)) + (parser != null ? "'" : "")));
			System.out.println("Set " + path + " to " + oldval * (isprice ? priceratio : ratio2D));
		};
		func.setValue("town.town_block_ratio", Integer::parseInt, false, false);
		final String path = "townBlockLimit";
		config.getMapList("levels.town_level").stream()
				.sorted((m1, m2) -> Integer.compare((int) m2.get(path), (int) m1.get(path))).forEach(map -> {
					int oldval = (int) map.get(path);
					conflines = conflines.map(s -> s.replace(path + ": " + oldval,
							path + ": " + String.format("%.0f", oldval * ratio2D)));
				});
		System.out.println("Set town level block limits");
		func.setValue("town.max_plots_per_resident", Integer::parseInt, false, false);
		func.setValue("town.min_plot_distance_from_town_plot", Integer::parseInt, false, false);
		func.setValue("town.min_distance_from_town_homeblock", Integer::parseInt, false, false);
		func.setValue("town.max_distance_between_homeblocks", Integer::parseInt, false, false);
		func.setValue("war.event.eco.wartime_town_block_loss_price", Float::parseFloat, true, true);
		func.setValue("economy.new_expand.price_claim_townblock", Float::parseFloat, true, true);
		func.setValue("economy.new_expand.price_purchased_bonus_townblock", Float::parseFloat, true, true);
		func.setValue("economy.new_expand.price_purchased_bonus_townblock_increase", Float::parseFloat, true, true);
		func.setValue("war.economy.townblock_won", Integer::parseInt, false, true);
		// Files.write(file.toPath().resolveSibling("config_test.yml"), conflines.collect(Collectors.toList()), StandardOpenOption.CREATE);
		Files.write(file.toPath(), conflines.collect(Collectors.toList()), StandardOpenOption.CREATE);
		System.out.println("Saved Towny configuration");
		System.out.println(); // TODO: TownblocklimitBONUS
		System.out.println("Setting town plots...");
		file = new File(file.getParentFile().getParentFile(), "data/townblocks");
		List<String> townblocks = new ArrayList<>();
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
					for (int i = 0; (float) i < ratio; i++) {
						for (int j = 0; (float) j < ratio; j++) {
							Files.copy(
									ff.toPath(), ff.toPath().resolveSibling((x * (int) ratio + i) + "_"
											+ (y * (int) ratio + j) + "_" + (int) newsize + ".data"),
									StandardCopyOption.REPLACE_EXISTING);
							townblocks.add(f.getName() + "," + (x * (int) ratio + i) + "," + (y * (int) ratio + j));
						}
					}
					ff.delete();
					C++;
				}
			}
			System.out.println("Finished with " + f.getName() + ", renaming " + C + " blocks");
		} // TODO: THIS DOESN'T WORK FOR SMALLER TO BIGGER CHANGE
		Files.write(file.toPath().resolveSibling("townblocks.txt"), townblocks, StandardOpenOption.TRUNCATE_EXISTING);
		for (File townfile : file.toPath().resolveSibling("towns").toFile().listFiles()) {
			if (townfile.isDirectory())
				continue;
			Path townpath = townfile.toPath();
			List<String> lines = Files.readAllLines(townpath);
			lines.replaceAll(line -> {
				if (!line.startsWith("homeBlock="))
					return line;
				String[] kv = line.split("=");
				if (kv.length > 1 && kv[0].equals("homeBlock")) {
					String[] loc = kv[1].split(",");
					return "homeBlock=" + loc[0] + "," + (int) (Integer.parseInt(loc[1]) * ratio) + ","
							+ (int) (Integer.parseInt(loc[2]) * ratio);
				}
				return line;
			});
			Files.write(townpath, lines);
		}
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
