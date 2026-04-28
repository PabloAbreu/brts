package org.brts.lowlevel.model.disc;

import java.util.List;

import org.brts.lowlevel.descriptor.PlaylistDescriptor;
import org.brts.lowlevel.model.bdmv.IndexBdmv;
import org.brts.lowlevel.model.bdmv.MovieObjects;

import lombok.Getter;
import lombok.Setter;

/**
 * Descriptor for full disc creation.
 *
 * All given information is given explicitly.
 */
@Getter
@Setter
public class LowLevelDiscDescriptor {

	/**
	 * Disc name. Should follow ISO/IEC 13346, UDF 2.50 or whatever
	 */
	private String discName;

	/**
	 * Parent folder.
	 *
	 * Passing parentFolder=/some/path and discName=MY_MOVIE will lead to creation of /some/path/MY_MOVIE/BDMV etc...
	 */
	private String parentFolder;

	/**
	 * index.bdmv model
	 */
	private IndexBdmv index;

	/**
	 * MovieObject.bdmv model
	 */
	private MovieObjects movieObjects;

	/**
	 * contents of BDMV/PLAYLIST/
	 */
	private List<PlaylistDescriptor> playlists;

}
