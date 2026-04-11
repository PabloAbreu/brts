package org.brts.lowlevel.igs.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Palette Definition Segment (PDS) — defines up to 256 palette entries.
 */
@Getter
@Setter
@ToString
public class IgsPalette {

	/** PTS from the PES header (90 kHz ticks). */
	private long pts;

	/** Palette id (8 bits). */
	private int id;

	/** Palette version number (8 bits). */
	private int version;

	/** The palette entries present in this segment. */
	private List<PaletteEntry> entries = new ArrayList<>();

}
