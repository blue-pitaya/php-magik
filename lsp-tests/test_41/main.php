<?php

namespace App\NSA {
    class A
    {
        public function get()
        {
            return 'A';
        }
    }
}

namespace App {
    use App\NSA\A;
    use App\NSA\A as Aliased;

    class Foo
    {
        public function call(A $a, Aliased $b, int $n, Foo $self)
        {
            $a->get();
        }
    }
}
