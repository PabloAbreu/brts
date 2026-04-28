package org.brts.lowlevel.igs.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * A single interactive page in an IGS menu. Each page has its own palette, set of BOGs (button groups), animations, and
 * UO (User Operation) mask.
 */
@Getter
@Setter
@ToString
public class IgsPage {

	/** Page id (8 bits). */
	private int id;

	/** Page version (8 bits). */
	private int version;

	/** User Operation mask table (8 bytes raw). */
	private byte[] uoMaskTable = new byte[8];

	/** In-effects animation sequence. */
	private IgsEffectSequence inEffects = new IgsEffectSequence();

	/** Out-effects animation sequence. */
	private IgsEffectSequence outEffects = new IgsEffectSequence();

	/** Animation frame rate code (8 bits). */
	private int animationFrameRateCode;

	/** Default selected button id ref (16 bits). */
	private int defaultSelectedButtonIdRef;

	/** Default activated button id ref (16 bits). */
	private int defaultActivatedButtonIdRef;

	/** Palette id reference (8 bits). */
	private int paletteIdRef;

	/** Button overlap groups on this page. */
	private List<IgsBog> bogs = new ArrayList<>();

}
