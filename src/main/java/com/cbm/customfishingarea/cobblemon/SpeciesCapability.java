package com.cbm.customfishingarea.cobblemon;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Smart capability snapshot for a Cobblemon species.
 * GUI uses this to enable only forms/aspects the species actually has;
 * disabled options explain why on click.
 */
public final class SpeciesCapability {
	public enum OptionKind {
		FORM,
		MEGA,
		GMAX,
		REGIONAL,
		ASPECT,
		SHINY,
		LEVEL,
		DELIVERY
	}

	public record Option(OptionKind kind, String id, String label, boolean enabled, String disabledReason) {
		public Component clickMessage() {
			if (enabled) {
				return Component.literal("Selected: " + label);
			}
			return Component.literal(disabledReason == null || disabledReason.isBlank()
					? (label + " is not available for this species.")
					: disabledReason);
		}
	}

	private final String speciesId;
	private final boolean present;
	private final List<String> forms;
	private final List<String> aspects;
	private final List<Option> options;

	private SpeciesCapability(String speciesId, boolean present, List<String> forms, List<String> aspects, List<Option> options) {
		this.speciesId = speciesId;
		this.present = present;
		this.forms = List.copyOf(forms);
		this.aspects = List.copyOf(aspects);
		this.options = List.copyOf(options);
	}

	public static SpeciesCapability missing(String speciesId) {
		return new SpeciesCapability(speciesId, false, List.of(), List.of(), List.of(
				new Option(OptionKind.FORM, "normal", "Normal", false, "Species not found: " + speciesId)
		));
	}

	public static SpeciesCapability inspect(String speciesId) {
		Optional<CobblemonBridge.SpeciesInfo> info = CobblemonBridge.speciesInfo(speciesId);
		if (info.isEmpty()) {
			return missing(speciesId);
		}
		CobblemonBridge.SpeciesInfo species = info.get();
		List<String> forms = new ArrayList<>(species.forms());
		if (forms.isEmpty()) {
			forms.add("normal");
		}
		List<String> aspects = new ArrayList<>(species.aspects());

		List<String> megaForms = forms.stream().filter(SpeciesCapability::isMega).toList();
		List<String> gmaxForms = forms.stream().filter(SpeciesCapability::isGmax).toList();
		List<String> regionalForms = forms.stream().filter(SpeciesCapability::isRegional).toList();

		List<Option> options = new ArrayList<>();
		options.add(new Option(OptionKind.FORM, "normal", "Normal / base form", true, null));

		for (String form : forms) {
			if ("normal".equalsIgnoreCase(form) || form.isBlank()) {
				continue;
			}
			OptionKind kind = OptionKind.FORM;
			if (isMega(form)) {
				kind = OptionKind.MEGA;
			} else if (isGmax(form)) {
				kind = OptionKind.GMAX;
			} else if (isRegional(form)) {
				kind = OptionKind.REGIONAL;
			}
			options.add(new Option(kind, form, pretty(form), true, null));
		}

		if (megaForms.isEmpty()) {
			options.add(new Option(OptionKind.MEGA, "mega", "Mega Evolution", false,
					species.displayName() + " has no Mega form."));
		}
		if (gmaxForms.isEmpty()) {
			options.add(new Option(OptionKind.GMAX, "gmax", "Gigantamax", false,
					species.displayName() + " has no Gigantamax form."));
		}
		if (regionalForms.isEmpty()) {
			options.add(new Option(OptionKind.REGIONAL, "regional", "Regional form", false,
					species.displayName() + " has no regional form (Alola/Galar/Hisui/…)."));
		}

		for (String aspect : aspects) {
			options.add(new Option(OptionKind.ASPECT, aspect, "Aspect: " + pretty(aspect), true, null));
		}

		options.add(new Option(OptionKind.SHINY, "shiny", "Shiny chance", true, null));
		options.add(new Option(OptionKind.LEVEL, "level", "Level range", true, null));
		options.add(new Option(OptionKind.DELIVERY, "delivery", "Delivery mode", true, null));

		return new SpeciesCapability(species.resourceName(), true, forms, aspects, dedupeOptions(options));
	}

	private static List<Option> dedupeOptions(List<Option> options) {
		Set<String> seen = new LinkedHashSet<>();
		List<Option> out = new ArrayList<>();
		for (Option option : options) {
			String key = option.kind + ":" + option.id.toLowerCase(Locale.ROOT);
			if (seen.add(key)) {
				out.add(option);
			}
		}
		return out;
	}

	public String speciesId() {
		return speciesId;
	}

	public boolean present() {
		return present;
	}

	public List<String> forms() {
		return forms;
	}

	public List<String> aspects() {
		return aspects;
	}

	public List<Option> options() {
		return options;
	}

	public List<Option> enabledForms() {
		return options.stream()
				.filter(o -> o.enabled && (o.kind == OptionKind.FORM || o.kind == OptionKind.MEGA
						|| o.kind == OptionKind.GMAX || o.kind == OptionKind.REGIONAL))
				.filter(o -> !"mega".equals(o.id) && !"gmax".equals(o.id) && !"regional".equals(o.id))
				.toList();
	}

	public Optional<Option> find(OptionKind kind, String id) {
		String needle = id == null ? "" : id.toLowerCase(Locale.ROOT);
		return options.stream()
				.filter(o -> o.kind == kind && o.id.equalsIgnoreCase(needle))
				.findFirst();
	}

	/** Validate a chosen form against this species; empty = ok. */
	public Optional<Component> validateForm(String form) {
		if (form == null || form.isBlank() || "normal".equalsIgnoreCase(form)) {
			return Optional.empty();
		}
		boolean known = forms.stream().anyMatch(f -> f.equalsIgnoreCase(form));
		if (known) {
			return Optional.empty();
		}
		return Optional.of(Component.literal(
				"Form '" + form + "' is not available for " + speciesId
						+ ". Available: " + String.join(", ", forms)));
	}

	public static boolean isMega(String form) {
		String f = form.toLowerCase(Locale.ROOT);
		return f.contains("mega");
	}

	public static boolean isGmax(String form) {
		String f = form.toLowerCase(Locale.ROOT);
		return f.contains("gmax") || f.contains("gigantamax");
	}

	public static boolean isRegional(String form) {
		String f = form.toLowerCase(Locale.ROOT);
		return f.contains("alola") || f.contains("galar") || f.contains("hisui")
				|| f.contains("paldea") || f.contains("valencian") || f.contains("galarian")
				|| f.contains("alolan") || f.contains("hisuian") || f.contains("paldean");
	}

	private static String pretty(String raw) {
		String s = raw.replace('_', ' ').replace('-', ' ');
		if (s.isEmpty()) {
			return raw;
		}
		StringBuilder out = new StringBuilder();
		for (String part : s.split("\\s+")) {
			if (part.isEmpty()) {
				continue;
			}
			if (!out.isEmpty()) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				out.append(part.substring(1));
			}
		}
		return out.toString();
	}
}
