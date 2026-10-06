<?php

class Foo
{
    public function calculate(?array $bars = null, int|string $key = 0): array
    {
        return $bars ?? [];
    }
}
