use rapier3d::prelude::{Group, InteractionGroups, InteractionTestMode};

pub const LEVEL_GROUP: InteractionGroups = InteractionGroups::new(
    Group::GROUP_1,
    Group::GROUP_1.union(Group::GROUP_2),
    InteractionTestMode::Or,
);
pub const ROPE_GROUP: InteractionGroups =
    InteractionGroups::new(Group::GROUP_2, Group::GROUP_1, InteractionTestMode::Or);
