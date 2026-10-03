package org.brts.lowlevel.batch;



import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single command-line flag of a {@link BatchStep}, with an optional value (omit it for boolean flags).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchArgument {

	/** The flag as typed on the command line, e.g. {@code --descriptor}. */
	@NotBlank
	private String flag;

	/** The flag value; {@code null} for boolean flags. */
	private String value;

}
