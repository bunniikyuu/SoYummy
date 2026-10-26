package igentuman.modtemplate.block_entity.catalyst;

import igentuman.modtemplate.block_entity.GlobalBlockEntity;

@FunctionalInterface
public interface CatalystFactory {
    Catalyst create(GlobalBlockEntity host);
}
