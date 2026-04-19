//! Flat octree for integer data

/// The max size we allow an octree buffer to occupy
const MAX_SIZE: i32 = i32::MAX - 8 * 2;

/// The fraction to resize an octree buffer by when it is full
const RESIZE_FRACTION: f32 = 1.5;

/// An octree containing collision data about sublevels
#[derive(Debug)]
pub struct SubLevelOctree {
    /// log_2 of the side length of the octree.
    /// Ex. for a 16x16x16 tree, this would be 4.
    ///
    pub log_size: i32,

    /// For the goal of cache-coherency, the octree representing a sublevels collision data
    /// is stored in a flat array.
    ///
    /// Nodes are stored as signed integers in the buffer, with the following format:
    /// - Positive -> Branch node; int is the index of first child (with the other 7 following afterward).
    /// - Negative -> Leaf node; int is the negation of the block ID - 1 (ex block ID 0 -> -1 in the array).
    /// - Zero -> Empty node
    ///
    /// This implementation is inspired by
    /// [this stackoverflow response](https://stackoverflow.com/questions/41946007/efficient-and-well-explained-implementation-of-a-quadtree-for-2d-collision-det#answer-48330314)
    pub buffer: Vec<i32>,

    /// The occupied size of the buffer in nodes.
    size: i32,

    /// The head of a singly linked list of free space in the buffer.
    free_space_index_head: i32,
    // last_access_positions: Vec<(i32, i32, i32)>,
    // last_access_indices: Vec<i32>
}

impl SubLevelOctree {
    /// Creates a new octree with a given log_2 size
    ///
    /// # Arguments
    ///
    /// * `log_size` - the log_2 of the size of the octree
    ///
    pub fn new(log_size: i32) -> Self {
        Self {
            log_size,
            buffer: vec![0; 256],
            size: 1,
            free_space_index_head: -1,
            // last_access_positions: vec![(0, 0, 0); 8],
            // last_access_indices: vec![-1; 8],
        }
    }

    /// @return a unique 0-7 index from a given x, y, z position in 0-1 ranges
    #[inline(always)]
    fn get_octant_index(x: i32, y: i32, z: i32) -> i32 {
        (x & 1) | ((y & 1) << 1) | ((z & 1) << 2)
    }

    /// Finds space to allocate 8 children in the buffer.
    /// This does not clear the returned index or space after the returned index.
    ///
    /// @return the index of the first child
    fn allocate_branch(&mut self) -> i32 {
        // start at the head of the free space list
        if self.free_space_index_head != -1 {
            let index = self.free_space_index_head - 1;
            self.free_space_index_head = self.buffer[index as usize];

            return index;
        }

        // if there is no free space, allocate at the end
        if self.size + 8 > self.buffer.len() as i32 {
            let new_size = (self.buffer.len() as f32 * RESIZE_FRACTION).ceil() as i32;

            if new_size > MAX_SIZE {
                panic!("Octree buffer is full");
            }

            let mut new_buffer = vec![0; new_size as usize];
            new_buffer[..self.buffer.capacity()].copy_from_slice(&self.buffer);
            self.buffer = new_buffer;
        }

        let index = self.size;
        self.size += 8;

        index
    }

    /// Splits a node at an index
    ///
    /// # Arguments
    ///
    /// * `index` - the index of the node to split
    ///
    fn split(&mut self, index: i32) {
        let node = self.buffer[index as usize];
        let branch_start_index = self.allocate_branch();
        self.buffer[index as usize] = branch_start_index;

        for i in 0..8 {
            self.buffer[(branch_start_index + i) as usize] = node;
        }
    }

    /// Merges a branch node at an index if possible
    ///
    /// # Arguments
    ///
    /// * `index` - the index of the branch node
    pub fn merge(&mut self, index: i32) {
        let branch_start_index = self.buffer[index as usize];
        let node = self.buffer[branch_start_index as usize];

        for i in 1..8 {
            if self.buffer[(branch_start_index + i) as usize] != node {
                return;
            }
        }

        if node == 0 {
            self.buffer[index as usize] = node;
            self.delete_children(branch_start_index);
            self.free_space_index_head = branch_start_index + 1;
        }
    }

    /// Deletes 8 children and adds the branch node to the free space list
    ///
    /// # Arguments
    ///
    /// * `branch_start_index` - the starting index of the 8 children
    fn delete_children(&mut self, branch_start_index: i32) {
        self.buffer[branch_start_index as usize] = self.free_space_index_head;
        self.free_space_index_head = branch_start_index;
        for i in 1..8 {
            self.buffer[(branch_start_index + i) as usize] = 0;
        }
    }

    /// Sets the block at a given position in the octree
    ///
    /// # Arguments
    ///
    /// * `x` - the x position
    /// * `y` - the y position
    /// * `z` - the z position
    /// * `block` - the block ID
    ///
    /// # Returns
    ///
    /// * `bool` - if the insert modified the tree
    pub fn insert(&mut self, x: i32, y: i32, z: i32, block: i32) -> bool {
        let mut shift = self.log_size - 1;
        let mut index = 0;
        let mut node = self.buffer[index as usize];

        let mut branch_index = 0;
        let mut branches_visited = vec![0; self.log_size as usize];

        while shift >= 0 {
            if node == self.block_id_to_node(block) {
                return false; // already equivalent
            }

            let octant_index = Self::get_octant_index(x >> shift, y >> shift, z >> shift);

            if node > 0 {
                branches_visited[branch_index as usize] = index;
                branch_index += 1;
            } else {
                self.split(index);
            }

            let branch_start_index = self.buffer[index as usize];
            index = branch_start_index + octant_index;
            node = self.buffer[index as usize];

            shift -= 1;
        }

        self.buffer[index as usize] = self.block_id_to_node(block);

        // reduce
        for i in (0..self.log_size).rev() {
            self.merge(branches_visited[i as usize]);
        }

        true
    }

    /// Queries the block at a given position in the octree
    ///
    /// # Arguments
    ///
    /// * `x` - the x position
    /// * `y` - the y position
    /// * `z` - the z position
    /// * `log_size_of_target` - the log size of the target
    ///
    /// # Returns
    ///
    /// * `i32` - the block ID at the position, or -2 if the position is empty
    pub fn query(&self, x: i32, y: i32, z: i32, log_size_of_target: i32) -> i32 {
        let size = 1 << self.log_size;
        // check if out of bounds
        if x < 0 || y < 0 || z < 0 || x >= size || y >= size || z >= size {
            return -2;
        }

        let mut shift = self.log_size - 1;
        let mut index = 0;
        let mut node: i32 = *unsafe { self.buffer.get_unchecked(index as usize) };

        while shift >= log_size_of_target {
            if node < 0 {
                return self.node_to_block_id(node);
            } else if node == 0 {
                return -2;
            }

            let octant_index = Self::get_octant_index(x >> shift, y >> shift, z >> shift);

            index = node + octant_index;
            node = *unsafe { self.buffer.get_unchecked(index as usize) };

            shift -= 1;
        }
        match node {
            ..0 => self.node_to_block_id(node),
            0 => -2,
            _ => -1,
        }
    }

    /// Converts a block ID to a node value to put in the buffer
    ///
    /// # Arguments
    ///
    /// * `block` - the block ID
    ///
    /// # Returns
    ///
    /// * `i32` - the node value
    fn block_id_to_node(&self, block: i32) -> i32 {
        -block - 1
    }

    /// Converts a node value in the buffer to a block ID
    ///
    /// # Arguments
    ///
    /// * `node` - the node value
    ///
    /// # Returns
    ///
    /// * `i32` - the block ID
    #[inline(always)]
    fn node_to_block_id(&self, node: i32) -> i32 {
        -node - 1
    }
}
