package org.brts.lowlevel.batch;



import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * Descriptor read by {@code brts low batch-run}: an ordered list of BRTS CLI invocations to execute sequentially.
 */
@Data
public class BatchRunDescriptor {

	/** Steps executed in order; execution stops at the first failing step. */
	@NotEmpty
	private List<@Valid BatchStep> steps = new ArrayList<>();

}
